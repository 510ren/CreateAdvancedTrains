"""Read-only audit of the 2026-10-25 split; --write-report saves its result.

This is a migration audit, not a product test or the future shared document
management procedure. Later approved requirements edits are expected to make
the exact migration comparison fail.
"""
from pathlib import Path
from collections import Counter
from urllib.parse import unquote
import hashlib
import json
import re
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
EXPECTED_SOURCE_HASH = '3b8d890584b90d72658894d22836a64f629056ba0e3f4ac525a8ddf8595b1681'
MANIFEST = HERE / '分割マニフェスト.json'
LINK = re.compile(r'!?\[([^\]\n]+)\]\(([^)\n]+)\)')
MARKER = re.compile(r'<!-- source:([\w-]+):start -->\n(.*?)<!-- source:\1:end -->', re.S)
REFERENCE = re.compile(r'第(\d+(?:\.\d+)?(?:[・～〜–－-]\d+(?:\.\d+)?)*)[節章]')

def digest(value):
    if isinstance(value, str): value = value.encode('utf-8')
    return hashlib.sha256(value).hexdigest()

def read(path):
    return path.read_bytes().decode('utf-8')

def check(condition, message):
    if not condition: raise AssertionError(message)

def restore(transformed, changes, section):
    cursor = 0
    original_cursor = 0
    chunks = []
    for change in changes:
        unchanged_length = change['start'] - original_cursor
        check(unchanged_length >= 0, 'Overlapping changes: ' + section)
        chunks.append(transformed[cursor:cursor + unchanged_length])
        cursor += unchanged_length
        after = change['after']
        check(transformed[cursor:cursor + len(after)] == after, 'Changed replacement: ' + section)
        chunks.append(change['before'])
        cursor += len(after)
        original_cursor = change['end']
    chunks.append(transformed[cursor:])
    return ''.join(chunks)

def validate_change(change, section, manifest, original_parent):
    before, after = change['before'], change['after']
    kind = change['kind']
    if kind == 'heading_level':
        check(section == 'preamble' and before == '# ' and after == '## ', 'Unexpected heading edit')
        return
    if kind == 'section_reference':
        check(REFERENCE.fullmatch(before) is not None, 'Non-reference changed')
        m = LINK.fullmatch(after)
        check(m is not None and m.group(1) == before, 'Reference label changed')
        nums = re.findall(r'\d+(?:\.\d+)?', before)
        first = 's-' + nums[0].replace('.', '-')
        units = {u['key']:u for u in manifest['units']}
        if len(nums) > 1 or '.' not in nums[0]:
            expected = '分割記録/節の対応表.md#' + first
        else:
            expected = Path(units[first]['file']).name + '#' + first
        check(m.group(2) == expected, 'Wrong section destination')
        return
    check(kind == 'path', 'Unapproved transformation kind')
    old, new = LINK.fullmatch(before), LINK.fullmatch(after)
    check(old is not None and new is not None and old.group(1) == new.group(1), 'Link label changed')
    oldpath = unquote(old.group(2).split('#')[0])
    newpath = unquote(new.group(2).split('#')[0])
    actual = (HERE.parent / newpath).resolve()
    previous = (original_parent / oldpath).resolve()
    check(actual.exists(), 'Missing relocated link')
    if previous.exists():
        check(actual == previous, 'Changed link target')
    else:
        matches = list(original_parent.rglob(Path(oldpath).name))
        matches = [p for p in matches if HERE.parent not in p.parents]
        check(len(matches) == 1 and actual == matches[0].resolve(), 'Ambiguous repaired link')

def run(write_report=False):
    manifest = json.loads(read(MANIFEST))
    snapshot = ROOT / manifest['snapshot']
    raw = snapshot.read_bytes()
    check(digest(raw) == EXPECTED_SOURCE_HASH == manifest['original_sha256'], 'Snapshot hash mismatch')
    check(len(raw) == manifest['original_bytes'], 'Snapshot length mismatch')
    original = raw.decode('utf-8')
    actual = {}
    occurrences = Counter()
    for name in manifest['documents']:
        content = read(ROOT / name)
        for match in MARKER.finditer(content):
            key, body = match.groups()
            occurrences[key] += 1
            actual[key] = (name, body)
        check(len(re.findall(r'<!-- source:.*?:start -->', content)) == len(list(MARKER.finditer(content))), 'Unpaired markers')
        check('\ufffd' not in content, 'Unicode replacement character')
        check(re.search(r'(?m)^(<<<<<<< |=======|>>>>>>> )', content) is None, 'Conflict marker')
    keys = [u['key'] for u in manifest['units']]
    check(len(set(keys)) == len(keys), 'Duplicate manifest entries')
    check(set(occurrences) == set(keys) and all(n == 1 for n in occurrences.values()), 'Missing/duplicate/unmapped source units')
    restored = []
    cursor = 0
    modifications = Counter()
    for unit in manifest['units']:
        key = unit['key']
        check(unit['start'] == cursor, 'Gap/overlap in original ranges')
        old = original[unit['start']:unit['end']]
        cursor = unit['end']
        check(digest(old) == unit['source_sha256'], 'Original section hash mismatch')
        name, body = actual[key]
        check(name == unit['file'], 'Section in wrong destination')
        check(digest(body) == unit['output_sha256'], 'Migrated section was edited: ' + key)
        for change in unit['changes']:
            check(old[change['start']:change['end']] == change['before'], 'Replacement source mismatch')
            validate_change(change, key, manifest, (ROOT / manifest['original_path']).parent)
            modifications[change['kind']] += 1
        recovered = restore(body, unit['changes'], key)
        check(recovered == old, 'Source content changed: ' + key)
        restored.append(recovered)
    check(cursor == len(original), 'Original tail missing')
    check(''.join(restored).encode('utf-8') == raw, 'Reassembled original differs')

    mapping = read(HERE / '節の対応表.md')
    for unit in manifest['units']:
        key = unit['key']
        filename = Path(unit['file']).name
        rows = [line for line in mapping.splitlines() if '<a id="' + key + '"></a>' in line]
        expected = '[' + Path(filename).stem + '](../' + filename + '#' + key + ')'
        check(len(rows) == 1 and expected in rows[0], 'Wrong/missing map row: ' + key)

    # Ensure headers/navigation do not hide extra text added after generation.
    for name, sha in manifest['generated_hashes'].items():
        check(digest((ROOT / name).read_bytes()) == sha, 'Generated file drift: ' + name)

    changed_inputs = {manifest['original_path']}
    for incoming in manifest['incoming_changes']:
        name = incoming['path']
        now = read(ROOT / name)
        before = restore(now, incoming['changes'], name)
        check(digest(before) == incoming['original_sha256'] == manifest['baseline'][name], 'Research content changed')
        for e in incoming['changes']:
            a,b = LINK.fullmatch(e['before']), LINK.fullmatch(e['after'])
            check(a is not None and b is not None and a.group(1) == b.group(1), 'Incoming link label changed')
            check((ROOT / name).parent.joinpath(unquote(b.group(2))).resolve() == (ROOT / manifest['original_path']).resolve(), 'Incoming link not to index')
        changed_inputs.add(name)
    unaffected = 0
    for name, sha in manifest['baseline'].items():
        if name in changed_inputs: continue
        check((ROOT / name).exists() and digest((ROOT / name).read_bytes()) == sha, 'Unrelated preexisting file changed: ' + name)
        unaffected += 1

    # Validate every active migration-page link. Frozen source links are preserved
    # as bytes, including pre-existing stale targets; no remote URL request is made.
    pages = [ROOT / x for x in manifest['documents'] + manifest['structural_files']]
    report = HERE / '検証結果.md'
    if report.exists(): pages.append(report)
    links = 0
    for page in pages:
        content = read(page)
        anchors = re.findall(r'<a id="([^"]+)"', content)
        check(len(anchors) == len(set(anchors)), 'Duplicate explicit anchors: ' + str(page))
        for match in LINK.finditer(content):
            target = match.group(2)
            if re.match(r'^[A-Za-z][\w+.-]*:', target): continue
            pathpart, sep, frag = target.partition('#')
            targetpath = (page.parent / unquote(pathpart)).resolve() if pathpart else page
            if write_report and targetpath == report.resolve() and not report.exists():
                continue  # The report is generated below and rechecked immediately.
            check(targetpath.exists(), f'Missing link: {page.name}: {target}')
            if sep:
                check('<a id="' + unquote(frag) + '"></a>' in read(targetpath), f'Missing anchor: {page.name}: {target}')
            links += 1

    found_pages = {p.resolve() for p in HERE.parent.glob('*.md')}
    check(found_pages == {(ROOT / p).resolve() for p in manifest['documents']}, 'Unmapped detail document')
    result = dict(original_bytes=len(raw), original_characters=len(original), original_sha256=digest(raw),
                  reconstructed_sha256=digest(''.join(restored)), source_units=len(keys),
                  detail_documents=len(manifest['documents']), mapping_rows_verified=len(manifest['units']), missing_or_duplicate_units=0,
                  text_differences_after_reference_normalization=0, local_links_checked=links,
                  transformations=dict(modifications), preexisting_broken_source_links_repaired=sum(x['repaired'] for x in manifest['link_rewrites']),
                  incoming_research_files_repaired=len(manifest['incoming_changes']), unrelated_files_unchanged=unaffected)
    if write_report:
        lines = ['# 整理3 分割検証結果', '', '検証日：2026-10-25', '',
                 '[整理3の索引](../../整理3.md) · [節の対応表](節の対応表.md)', '',
                 '## 結果', '',
                 '**分割前の本文内容に差異はありません。** ファイル配置、案内見出し、リンクの記法・相対パスのみを変更しました。原文区間を元の順へ戻し、記録した参照変更だけを逆変換すると、分割前の全文とバイト単位で一致します。', '',
                 '| 検証項目 | 結果 |', '| --- | --- |',
                 f'| 分割前の大きさ | {len(raw):,} bytes／{len(original):,}文字 |',
                 f'| 分野別の詳細文書 | {len(manifest["documents"])}ファイル |',
                 f'| 原文区間 | {len(keys)}区間を全て1回ずつ配置 |',
                 '| 本文の欠落・重複配置 | 0件 |',
                 '| 参照変更を戻した後の本文差分 | 0件 |',
                 '| 原文の全バイト再構成 | 一致 |',
                 f'| 分割前から古かった本文リンク | {result["preexisting_broken_source_links_repaired"]}箇所を同名・一意の現存資料へ修正 |',
                 f'| 調査資料から索引への既存のリンク切れ | {len(manifest["incoming_changes"])}ファイル・各1箇所を修正。本文は同一 |',
                 f'| その他の既存ファイル | 対象範囲docs/organize内の{unaffected}ファイルのハッシュ不変 |',
                 f'| 現行ページの相対リンク・明示アンカー | {links}件で存在確認済み |', '',
                 '## 原文保存と差異の範囲', '',
                 f'- [固定保存版](整理3_2026-10-25_分割前.md)のSHA-256：`{digest(raw)}`',
                 f'- 再構成した全文のSHA-256：`{digest("".join(restored))}`',
                 '- 205区間は、元の大見出し・節見出しの区切りと冒頭の保存情報です。分野への移動で並び順が変わっても、元の範囲・順序・配置先・変換履歴を記録しています。',
                 '- 原文の表題は共通文書内で見出しレベルだけを1段下げています。各節の番号・見出し文言、表・箇条書き、数値、決定／提案／未決定の表記、元からある要約・履歴は維持しています。',
                 '- 「第○節」等の参照文字列は表示文字を変えずリンク化しました。単独節は本文、章全体・複数節・範囲は全旧見出しの対応表へ移動できます。',
                 '- Markdownの相対パスを新しい階層へ合わせ、移動前から切れていた参照は同じ資料名の一意な現存ファイルへ修正しました。出典の中身やWebのURLは変更していません。',
                 '- 固定保存版の相対リンクも含めて、保存版自体は一切書き換えていません。現行の案内には索引・分野別文書を使ってください。', '',
                 '## 確認方法と限界', '',
                 '- 機械照合：原文範囲の隙間／重なり、全区間の一意配置、許可した変換の種類、全体の再構成、生成後の文書ハッシュ、本文リンク・明示アンカー、研究資料のリンク以外の不変性を確認しました。',
                 '- 変更の根拠は[分割マニフェスト](分割マニフェスト.json)、配置先は[対応表](節の対応表.md)から追跡できます。',
                 '- [検証用スクリプト](verify_split.py)は分割時点との厳密な比較用です。将来の承認済み要件更新後は不一致になります。これを将来の変更禁止や全チャット共通の管理手順とは扱いません。',
                 '- 保存後の通常の実行例：プロジェクトのルートで `python -X utf8 docs/organize/整理3/分割記録/verify_split.py`。通常実行は読取専用です。',
                 '- 元の仕様自体に矛盾がないことや、調査の正確さ・実装・Minecraftでの動作を証明する検査ではありません。外部Webの到達確認や、保存版・無関係な調査資料に元からある全リンクの修正は範囲外です。',
                 '- 本文区間の機械照合とは別に、索引・分野別の配置・対応表・関連先を確認しました。共通のドキュメント管理手順は本ゴール後に利用者と別途作成します。', '']
        report.write_bytes('\n'.join(lines).encode('utf-8'))
        return run(False)
    return result

if __name__ == '__main__':
    print(json.dumps(run('--write-report' in sys.argv), ensure_ascii=False, indent=2))
