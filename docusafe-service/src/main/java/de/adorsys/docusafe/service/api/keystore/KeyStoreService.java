package de.adorsys.docusafe.service.api.keystore;

import de.adorsys.docusafe.service.api.keystore.types.*;

import javax.crypto.SecretKey;
import java.security.KeyStore;
import java.security.PrivateKey;

/**
 * Created by peter on 11.01.18.
 */
public interface KeyStoreService {

    KeyStore createKeyStore(KeyStoreAuth keyStoreAuth,
                            KeyStoreType keyStoreType,
                            KeyStoreCreationConfig config);

    PublicKeyList getPublicKeys(KeyStoreAccess keyStoreAccess);

    PrivateKey getPrivateKey(KeyStoreAccess keyStoreAccess, KeyID keyID);

    SecretKey getSecretKey(KeyStoreAccess keyStoreAccess, KeyID keyID);

    SecretKeyIDWithKey getRandomSecretKeyID(KeyStoreAccess keyStoreAccess);

}
