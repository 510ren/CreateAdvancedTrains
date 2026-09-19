package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param status 仕様書に個別説明がないため、{@code status}が示す現在または判定後の状態。
 * @param reason 処理を行う理由を表す文字列。
 */
public record CatErrorRecoveryResult(
        Status status,
        Optional<String> reason) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param status 仕様書に個別説明がないため、{@code status}が示す現在または判定後の状態。
     * @param reason 処理を行う理由を表す文字列。
     */
    public CatErrorRecoveryResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
    }

    public enum Status {
        RECOVERED,
        REJECTED,
        NO_ERROR
    }
}
