package org.adorsys.documentsafe.layer02service.impl;

import org.adorsys.cryptoutils.exceptions.BaseException;
import org.adorsys.documentsafe.layer02service.BucketService;
import org.adorsys.documentsafe.layer02service.DocumentGuardService;
import org.adorsys.documentsafe.layer02service.exceptions.NoDocumentGuardExists;
import org.adorsys.documentsafe.layer02service.generators.SecretKeyGenerator;
import org.adorsys.documentsafe.layer02service.impl.guardHelper.GuardKeyHelper;
import org.adorsys.documentsafe.layer02service.impl.guardHelper.GuardKeyHelperFactory;
import org.adorsys.documentsafe.layer02service.impl.guardHelper.KeySourceAndGuardKeyID;
import org.adorsys.documentsafe.layer02service.keysource.KeyStoreBasedSecretKeySourceImpl;
import org.adorsys.documentsafe.layer02service.serializer.DocumentGuardSerializer;
import org.adorsys.documentsafe.layer02service.serializer.DocumentGuardSerializer01;
import org.adorsys.documentsafe.layer02service.serializer.DocumentGuardSerializerRegistery;
import org.adorsys.documentsafe.layer02service.types.DocumentKey;
import org.adorsys.documentsafe.layer02service.types.DocumentKeyID;
import org.adorsys.documentsafe.layer02service.types.GuardKey;
import org.adorsys.documentsafe.layer02service.types.complextypes.DocumentGuardLocation;
import org.adorsys.documentsafe.layer02service.types.complextypes.DocumentKeyIDWithKey;
import org.adorsys.documentsafe.layer02service.types.complextypes.DocumentKeyIDWithKeyAndAccessType;
import org.adorsys.documentsafe.layer02service.types.complextypes.KeyStoreAccess;
import org.adorsys.documentsafe.layer03business.types.AccessType;
import org.adorsys.encobject.complextypes.BucketPath;
import org.adorsys.encobject.domain.ContentMetaInfo;
import org.adorsys.encobject.domain.ObjectHandle;
import org.adorsys.encobject.keysource.KeySource;
import org.adorsys.encobject.params.EncryptionParams;
import org.adorsys.encobject.service.BlobStoreKeystorePersistence;
import org.adorsys.encobject.service.ExtendedStoreConnection;
import org.adorsys.encobject.service.JWEPersistence;
import org.adorsys.encobject.service.KeystorePersistence;
import org.adorsys.encobject.service.PersistentObjectWrapper;
import org.adorsys.encobject.types.KeyID;
import org.adorsys.encobject.types.KeyStoreType;
import org.adorsys.encobject.types.OverwriteFlag;
import org.adorsys.jkeygen.keystore.SecretKeyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DocumentGuardServiceImpl implements DocumentGuardService {
    private final static Logger LOGGER = LoggerFactory.getLogger(DocumentGuardServiceImpl.class);
    private final static String ACCESS_TYPE = "AccessType";
    private final static String KEYSTORE_TYPE = "KeyStoreType";

    private KeystorePersistence keystorePersistence;
    private JWEPersistence jwePersistence;
    private BucketService bucketService;


    private DocumentGuardSerializerRegistery serializerRegistry = DocumentGuardSerializerRegistery.getInstance();

    public DocumentGuardServiceImpl(ExtendedStoreConnection extendedStoreConnection) {
        this.jwePersistence = new JWEPersistence(extendedStoreConnection);
        this.keystorePersistence = new BlobStoreKeystorePersistence(extendedStoreConnection);
        this.bucketService = new BucketServiceImpl(extendedStoreConnection);
    }

    /**
     * erzeugt eine DocumentKeyIDWithKey
     */
    @Override
    public DocumentKeyIDWithKey createDocumentKeyIdWithKey() {
        // Eine zufällige DocumentKeyID erzeugen
        DocumentKeyID documentKeyID = new DocumentKeyID("DK" + UUID.randomUUID().toString());

        // Für die DocumentKeyID einen DocumentKey erzeugen
        SecretKeyGenerator secretKeyGenerator = new SecretKeyGenerator("AES", 256);
        SecretKeyData secretKeyData = secretKeyGenerator.generate(documentKeyID.getValue(), null);
        DocumentKey documentKey = new DocumentKey(secretKeyData.getSecretKey());
        return new DocumentKeyIDWithKey(documentKeyID, documentKey);
    }

    @Override
    public void createDocumentGuardFor(GuardKeyType guardKeyType,
                                       KeyStoreAccess keyStoreAccess,
                                       DocumentKeyIDWithKeyAndAccessType documentKeyIDWithKeyAndAccessType,
                                       OverwriteFlag overwriteFlag) {
        LOGGER.info("start create document guard for " + documentKeyIDWithKeyAndAccessType + " at " + keyStoreAccess.getKeyStorePath());
        GuardKeyHelper helper = GuardKeyHelperFactory.getHelper(guardKeyType);
        KeySourceAndGuardKeyID keySourceAndGuardKeyID = helper.getKeySourceAndGuardKeyID(keystorePersistence, keyStoreAccess, documentKeyIDWithKeyAndAccessType);
        createDocumentGuard(keyStoreAccess, documentKeyIDWithKeyAndAccessType, keySourceAndGuardKeyID, overwriteFlag);
        LOGGER.info("finished create document guard for " + documentKeyIDWithKeyAndAccessType + " at " + keyStoreAccess.getKeyStorePath());
    }


    /**
     * Loading the secret key from the guard.
     */
    @Override
    public DocumentKeyIDWithKeyAndAccessType loadDocumentKeyIDWithKeyAndAccessTypeFromDocumentGuard(KeyStoreAccess keyStoreAccess, DocumentKeyID documentKeyID) {
        LOGGER.info("start load " + documentKeyID + " from document guard at " + keyStoreAccess.getKeyStorePath());

        KeyStore userKeystore = keystorePersistence.loadKeystore(keyStoreAccess.getKeyStorePath().getObjectHandle(), keyStoreAccess.getKeyStoreAuth().getReadStoreHandler());

        // load guard file
        KeySource keySource = new KeyStoreBasedSecretKeySourceImpl(userKeystore, keyStoreAccess.getKeyStoreAuth().getReadKeyHandler());
        BucketPath guardBucketPath = DocumentGuardLocation.getBucketPathOfGuard(keyStoreAccess.getKeyStorePath(), documentKeyID);
        if (!bucketService.fileExists(guardBucketPath)) {
            throw new NoDocumentGuardExists(guardBucketPath);
        }
        LOGGER.debug("loadDocumentKey for " + guardBucketPath);
        PersistentObjectWrapper wrapper = jwePersistence.loadObject(guardBucketPath.getObjectHandle(), keySource);
        ContentMetaInfo metaIno = wrapper.getMetaIno();
        Map<String, Object> addInfos = metaIno.getAddInfos();
        String accesstypestring = (String) addInfos.get(ACCESS_TYPE);
        if (accesstypestring == null) {
            throw new BaseException("PROGRAMMING ERROR. AccessType for Guard with KeyID " + documentKeyID + " not known");
        }
        String keyStoreTypeString = (String) addInfos.get(KEYSTORE_TYPE);
        if (keyStoreTypeString == null) {
            throw new BaseException("PROGRAMMING ERROR. KeyStoreType for Guard with KeyID " + documentKeyID + " not known");
        }
        KeyStoreType keyStoreType = new KeyStoreType(keyStoreTypeString);

        AccessType accessType = AccessType.WRITE.valueOf(accesstypestring);
        String serializerId = (String) addInfos.get(serializerRegistry.SERIALIZER_HEADER_KEY);
        DocumentGuardSerializer serializer = serializerRegistry.getSerializer(serializerId);
        DocumentKey documentKey = serializer.deserializeSecretKey(wrapper.getData(), keyStoreType);

        LOGGER.info("finished load " + documentKeyID + " from document guard at " + keyStoreAccess.getKeyStorePath());
        return new DocumentKeyIDWithKeyAndAccessType(new DocumentKeyIDWithKey(documentKeyID, documentKey), accessType);
    }


    private void createDocumentGuard(KeyStoreAccess keyStoreAccess,
                                     DocumentKeyIDWithKeyAndAccessType documentKeyIDWithKeyAndAccessType,
                                     KeySourceAndGuardKeyID keySourceAndGuardKeyID,
                                     OverwriteFlag overwriteFlag) {
        LOGGER.info("start persist document guard for " + documentKeyIDWithKeyAndAccessType + " at " + keyStoreAccess.getKeyStorePath());
        KeyStoreType keyStoreType = new KeyStoreType("UBER");
        ObjectHandle documentGuardHandle = DocumentGuardLocation.getBucketPathOfGuard(keyStoreAccess.getKeyStorePath(),
                documentKeyIDWithKeyAndAccessType.getDocumentKeyIDWithKey().getDocumentKeyID()).getObjectHandle();
        EncryptionParams encParams = null;

        // Den DocumentKey serialisieren, in der MetaInfo die SerializerID vermerken
        ContentMetaInfo metaInfo = new ContentMetaInfo();
        metaInfo.setAddInfos(new HashMap<>());
        DocumentGuardSerializer documentGuardSerializer = serializerRegistry.defaultSerializer();
        metaInfo.getAddInfos().put(serializerRegistry.SERIALIZER_HEADER_KEY, documentGuardSerializer.getSerializerID());
        metaInfo.getAddInfos().put(ACCESS_TYPE, documentKeyIDWithKeyAndAccessType.getAccessType());
        metaInfo.getAddInfos().put(KEYSTORE_TYPE, keyStoreType.getValue());
        GuardKey guardKey = new GuardKey(documentGuardSerializer.serializeSecretKey(
                documentKeyIDWithKeyAndAccessType.getDocumentKeyIDWithKey().getDocumentKey(), keyStoreType));

        jwePersistence.storeObject(guardKey.getValue(), metaInfo, documentGuardHandle, keySourceAndGuardKeyID.keySource,
                new KeyID(keySourceAndGuardKeyID.guardKeyID.getValue()), encParams, overwriteFlag);
        LOGGER.info("finished persist document guard for " + documentKeyIDWithKeyAndAccessType + " at " + keyStoreAccess.getKeyStorePath());
    }


}
