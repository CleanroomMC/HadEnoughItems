package mezz.jei.search;

import mezz.jei.config.Config;

public class TokenInfo {

    public static TokenInfo parseRawToken(String token) {
        if (token.isEmpty()) {
            return null;
        }
        PrefixInfo prefixInfo = PrefixInfo.get(token.charAt(0));
        if (prefixInfo != null && prefixInfo.getMode() == Config.SearchMode.REQUIRE_PREFIX) {
            token = token.substring(1);
            if (token.isEmpty()) {
                return null;
            }
            return new TokenInfo(token, prefixInfo);
        }
        return new TokenInfo(token, PrefixInfo.NO_PREFIX);
    }

    public final String token;
    public final PrefixInfo prefixInfo;

    public TokenInfo(String token, PrefixInfo prefixInfo) {
        this.token = token;
        this.prefixInfo = prefixInfo;
    }

}
