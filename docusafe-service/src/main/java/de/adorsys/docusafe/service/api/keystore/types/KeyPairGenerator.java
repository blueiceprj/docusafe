package de.adorsys.docusafe.service.api.keystore.types;

import javax.security.auth.callback.CallbackHandler;

/**
 * Created by peter on 26.02.18 at 17:09.
 */
public interface KeyPairGenerator {
    KeyPairEntry generateSignatureKey(String alias, CallbackHandler keyPassHandler);
    KeyPairEntry generateEncryptionKey(String alias, CallbackHandler keyPassHandler);
}
