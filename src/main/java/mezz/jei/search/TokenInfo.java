package mezz.jei.search;

import mezz.jei.config.Config;

import java.util.Optional;

public class TokenInfo {

    public static TokenInfo parseRawToken(String token) {
        if (token.isEmpty()) {
            return null;
        }
        PrefixInfo prefixInfo = PrefixInfo.get(token.charAt(0));
        if (prefixInfo == null || prefixInfo.getMode() == Config.SearchMode.DISABLED) {
            return new TokenInfo(token, PrefixInfo.NO_PREFIX);
        }
        if (token.length() == 1) {
            return null;
        }
        return new TokenInfo(token.substring(1), prefixInfo);
    }

    public static Optional<TokenInfo> parseToken(String token) {
        return Optional.ofNullable(parseRawToken(token));
    }

    public final String token;
    public final PrefixInfo prefixInfo;

    public TokenInfo(String token, PrefixInfo prefixInfo) {
        this.token = token;
        this.prefixInfo = prefixInfo;
    }

}
