package de.adorsys.docusafe.business.impl;

import de.adorsys.common.exceptions.BaseException;
import de.adorsys.common.exceptions.BaseExceptionHandler;
import de.adorsys.dfs.connection.api.complextypes.BucketDirectory;
import de.adorsys.dfs.connection.api.complextypes.BucketPath;
import de.adorsys.dfs.connection.api.domain.Payload;
import de.adorsys.dfs.connection.api.domain.PayloadStream;
import de.adorsys.dfs.connection.api.service.api.DFSConnection;
import de.adorsys.dfs.connection.api.service.impl.SimplePayloadImpl;
import de.adorsys.dfs.connection.api.service.impl.SimplePayloadStreamImpl;
import de.adorsys.dfs.connection.api.types.ListRecursiveFlag;
import de.adorsys.dfs.connection.api.types.properties.ConnectionProperties;
import de.adorsys.dfs.connection.impl.factory.DFSConnectionFactory;
import de.adorsys.docusafe.business.DocumentSafeService;
import de.adorsys.docusafe.business.exceptions.UserExistsException;
import de.adorsys.docusafe.business.impl.caches.*;
import de.adorsys.docusafe.business.impl.caches.guava.UserDFSCredentialsCacheGuavaImpl;
import de.adorsys.docusafe.business.impl.caches.guava.UserPathSecretKeyCacheGuavaImpl;
import de.adorsys.docusafe.business.impl.caches.guava.UserPublicKeyListCacheGuavaImpl;
import de.adorsys.docusafe.business.impl.caches.guava.UsersPrivateKeyStoreCacheGuavaImpl;
import de.adorsys.docusafe.business.impl.jsonserialisation.Class2JsonHelper;
import de.adorsys.docusafe.business.types.*;
import de.adorsys.docusafe.service.api.keystore.types.*;
import lombok.extern.slf4j.Slf4j;
import de.adorsys.docusafe.service.api.bucketpathencryption.BucketPathEncryptionService;
import de.adorsys.docusafe.service.api.cmsencryption.CMSEncryptionService;
import de.adorsys.docusafe.service.api.keystore.KeyStoreService;
import de.adorsys.docusafe.service.api.types.DocumentContent;
import de.adorsys.docusafe.service.api.types.UserID;
import de.adorsys.docusafe.service.api.types.UserIDAuth;
import de.adorsys.docusafe.service.impl.bucketpathencryption.BucketPathEncryptionServiceImpl;
import de.adorsys.docusafe.service.impl.cmsencryption.services.CMSEncryptionServiceImpl;
import de.adorsys.docusafe.service.impl.keystore.service.KeyStoreServiceImpl;
import org.bouncycastle.cms.CMSEnvelopedData;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
public class DocumentSafeServiceImpl implements DocumentSafeService {
    private final DFSConnection systemDFS;
    private final Class2JsonHelper class2JsonHelper = new Class2JsonHelper();
    private final BucketPathEncryptionService bucketPathEncryptionService = new BucketPathEncryptionServiceImpl();
    private final CMSEncryptionService cmsEncryptionService = new CMSEncryptionServiceImpl();
    private final DFSCredentials defaultUserDFSCredentials;
    private final CacheWrapper cacheWrapper;

    private final UsersPrivateKeyStoreCache usersPrivateKeyStoreCache = new UsersPrivateKeyStoreCacheGuavaImpl();
    private final UserPublicKeyListCache userPublicKeyListCache = new UserPublicKeyListCacheGuavaImpl();
    private final UserPathSecretKeyCache userPathSecretKeyCache = new UserPathSecretKeyCacheGuavaImpl();
    private final UserDFSCredentialsCache userDFSCredentialsCache = new UserDFSCredentialsCacheGuavaImpl();

    private DFSCredentials getDefaultDFSCredentials(ConnectionProperties props) {
        DFSCredentials dfsCredentials = new DFSCredentials(props);
        return dfsCredentials;
    }

    private final KeyStoreService keyStoreService = new KeyStoreServiceImpl();

    public DocumentSafeServiceImpl(DFSConnection dfsConnection) {
        {
            DFSCredentials dfsCredentials = new DFSCredentials(dfsConnection.getConnectionProperties());
            dfsCredentials.addRootBucket(DFSCredentials.TYPE.SYSTEM_DFS);
            systemDFS = DFSConnectionFactory.get(dfsCredentials.getProperties());
        }
        {
            DFSCredentials dfsCredentials = new DFSCredentials(dfsConnection.getConnectionProperties());
            dfsCredentials.addRootBucket(DFSCredentials.TYPE.USERS_DFS);
            defaultUserDFSCredentials = new DFSCredentials(dfsCredentials.getProperties());
        }
        cacheWrapper = new CacheWrapper();
        log.debug(cacheWrapper.toString());
    }

    @Override
    public void createUser(UserIDAuth userIDAuth) {
        synchronized (userIDAuth.getUserID().getValue().intern()) {
            try {
                // create userspace in systemdfs
                if (userExists(userIDAuth.getUserID())) {
                    throw new UserExistsException(userIDAuth.getUserID());
                }
                // create and persist public keystore
                KeyStoreAccess publicKeyStoreAccess = null;
                {
                    KeyStoreAuth keyStoreAuth = new KeyStoreAuth(new ReadStorePassword(userIDAuth.getReadKeyPassword().getValue()), userIDAuth.getReadKeyPassword());
                    KeyStore usersSystemKeyStore = keyStoreService.createKeyStore(keyStoreAuth, KeyStoreType.DEFAULT, new KeyStoreCreationConfig(1, 0, 0));
                    persistKeystore(userIDAuth, usersSystemKeyStore, systemDFS);
                    publicKeyStoreAccess = new KeyStoreAccess(usersSystemKeyStore, keyStoreAuth);
                }

                // create and persist encrypted dfscredentials
                DFSCredentials userDFSCredentials = null;
                {
                    userDFSCredentials = new DFSCredentials(defaultUserDFSCredentials);
                    // retrieve public key of public keystore once to encrypt DFSCredentials
                    storeUserDFSCredentials(userIDAuth, publicKeyStoreAccess, userDFSCredentials);
                    userDFSCredentialsCache.put(new UserAuthCacheKey(userIDAuth), userDFSCredentials);
                }

                // create users DFS
                DFSConnection usersDFSConnection = null;
                {
                    usersDFSConnection = DFSConnectionFactory.get(userDFSCredentials.getProperties());
                }

                // create and persist private keystore
                KeyStoreAccess privateKeyStoreAccess = null;
                {
                    KeyStoreAuth keyStoreAuth = new KeyStoreAuth(new ReadStorePassword(userIDAuth.getReadKeyPassword().getValue()), userIDAuth.getReadKeyPassword());
                    KeyStore usersSystemKeyStore = keyStoreService.createKeyStore(keyStoreAuth, KeyStoreType.DEFAULT, new KeyStoreCreationConfig(5, 0, 1));
                    persistKeystore(userIDAuth, usersSystemKeyStore, usersDFSConnection);
                    privateKeyStoreAccess = new KeyStoreAccess(usersSystemKeyStore, keyStoreAuth);
                    usersPrivateKeyStoreCache.put(new UserAuthCacheKey(userIDAuth), privateKeyStoreAccess);
                }

                // extract public keys and store them in userspace of systemdfs
                {
                    List<PublicKeyIDWithPublicKey> publicKeys = keyStoreService.getPublicKeys(privateKeyStoreAccess);
                    Payload payload = class2JsonHelper.keyListToContent(publicKeys);
                    systemDFS.putBlob(FolderHelper.getPublicKeyListPath(userIDAuth.getUserID()), payload);
                    userPublicKeyListCache.put(userIDAuth.getUserID(), new PublicKeyList(publicKeys));
                }


            } catch (Exception e) {
                throw BaseExceptionHandler.handle(e);
            }
        }
    }

    @Override
    public void destroyUser(UserIDAuth userIDAuth) {
        synchronized (userIDAuth.getUserID().getValue().intern()) {
            final DFSConnection usersDFSConnection = getUsersDFS(userIDAuth);
            usersDFSConnection.removeBlobFolder(FolderHelper.getRootDirectory(userIDAuth.getUserID()));
            systemDFS.removeBlobFolder(FolderHelper.getRootDirectory(userIDAuth.getUserID()));
        }
    }

    @Override
    public boolean userExists(UserID userID) {
        return (systemDFS.blobExists(FolderHelper.getKeyStorePath(userID)));
    }

    @Override
    public void registerDFSCredentials(UserIDAuth userIDAuth, DFSCredentials dfsCredentials) {
        try {
            // retrieve the old dfs and store them in the new dfs
            DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth);
            DFSConnection oldUsersDFSConnection = dfsAndKeystoreAndPath.usersDFS;
            DFSConnection newUsersDFSConnection = DFSConnectionFactory.get(dfsCredentials.getProperties());
            int numberOfFilesCopied = 0;
            for (BucketPath bucketPath : oldUsersDFSConnection.list(FolderHelper.getRootDirectory(userIDAuth.getUserID()), ListRecursiveFlag.TRUE)) {
                    // nothing has to be en- or decrypted. The data just has to be moved from one dfs to the next dfs
                    Payload blob = oldUsersDFSConnection.getBlob(bucketPath);
                    newUsersDFSConnection.putBlob(bucketPath, blob);
                    numberOfFilesCopied++;
            }
            log.debug("copied " + numberOfFilesCopied + " from old dfs to new dfs");

            // store new dfs and overwrite old dfs
            // create and persist encrypted dfscredentials
            {
                // retrieve public key of public keystore once to encrypt DFSCredentials
                KeyStoreAccess publicKeyStoreAccess = getKeyStoreAccess(systemDFS, userIDAuth);
                storeUserDFSCredentials(userIDAuth, publicKeyStoreAccess, dfsCredentials);
                userDFSCredentialsCache.put(new UserAuthCacheKey(userIDAuth), dfsCredentials);
            }

            // now delete all the old data
            oldUsersDFSConnection.removeBlobFolder(FolderHelper.getRootDirectory(userIDAuth.getUserID()));
            log.debug("deleted user from old dfs");

        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public void storeDocument(UserIDAuth userIDAuth, DSDocument dsDocument) {
        DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, dsDocument.getDocumentFQN());
        Payload payload = encryptDataForUserWithRandomKey(userIDAuth.getUserID(), new SimplePayloadImpl(dsDocument.getDocumentContent().getValue()));
        dfsAndKeystoreAndPath.usersDFS.putBlob(dfsAndKeystoreAndPath.encryptedBucketPath, payload);
    }

    @Override
    public DSDocument readDocument(UserIDAuth userIDAuth, DocumentFQN documentFQN) {
        try {
            DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, documentFQN);
            Payload payload = dfsAndKeystoreAndPath.usersDFS.getBlob(dfsAndKeystoreAndPath.encryptedBucketPath);
            CMSEnvelopedData cmsEnvelopedData = new CMSEnvelopedData(payload.getData());
            Payload decrypt = cmsEncryptionService.decrypt(cmsEnvelopedData, dfsAndKeystoreAndPath.privateKeystoreAccess);
            return new DSDocument(documentFQN, new DocumentContent(decrypt.getData()));
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public void storeDocumentStream(UserIDAuth userIDAuth, DSDocumentStream dsDocumentStream) {
        try {
            DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, dsDocumentStream.getDocumentFQN());
            PublicKeyIDWithPublicKey publicKeyIDWithPublicKey = getPublicKeyIDWithPublicKey(userIDAuth.getUserID());
            InputStream encryptedStream = cmsEncryptionService.buildEncryptionInputStream(dsDocumentStream.getDocumentStream(), publicKeyIDWithPublicKey.getPublicKey(), publicKeyIDWithPublicKey.getKeyID());
            dfsAndKeystoreAndPath.usersDFS.putBlobStream(dfsAndKeystoreAndPath.encryptedBucketPath, new SimplePayloadStreamImpl(encryptedStream));
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }

    }

    @Override
    public DSDocumentStream readDocumentStream(UserIDAuth userIDAuth, DocumentFQN documentFQN) {
        try {
            DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, documentFQN);
            PayloadStream payloadStream = null;
            payloadStream = dfsAndKeystoreAndPath.usersDFS.getBlobStream(dfsAndKeystoreAndPath.encryptedBucketPath);
            InputStream decryptedStream = cmsEncryptionService.buildDecryptionInputStream(payloadStream.openStream(), dfsAndKeystoreAndPath.privateKeystoreAccess);
            EncryptionInputStreamWrapper encryptionInputStreamWrapper = new EncryptionInputStreamWrapper(decryptedStream, payloadStream.openStream());
            return new DSDocumentStream(documentFQN, encryptionInputStreamWrapper);
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public void deleteDocument(UserIDAuth userIDAuth, DocumentFQN documentFQN) {
        DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, documentFQN);
        dfsAndKeystoreAndPath.usersDFS.removeBlob(dfsAndKeystoreAndPath.encryptedBucketPath);
    }

    @Override
    public boolean documentExists(UserIDAuth userIDAuth, DocumentFQN documentFQN) {
        DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, documentFQN);
        return dfsAndKeystoreAndPath.usersDFS.blobExists(dfsAndKeystoreAndPath.encryptedBucketPath);
    }

    @Override
    public void deleteFolder(UserIDAuth userIDAuth, DocumentDirectoryFQN documentDirectoryFQN) {
        DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, documentDirectoryFQN);
        dfsAndKeystoreAndPath.usersDFS.removeBlobFolder(dfsAndKeystoreAndPath.encryptedBucketDirectory);
    }

    @Override
    public List<DocumentFQN> list(UserIDAuth userIDAuth, DocumentDirectoryFQN documentDirectoryFQN, ListRecursiveFlag recursiveFlag) {
        List<DocumentFQN> retList = new ArrayList<>();
        DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, documentDirectoryFQN);
        List<BucketPath> list = dfsAndKeystoreAndPath.usersDFS.list(dfsAndKeystoreAndPath.encryptedBucketDirectory, recursiveFlag);
        String homeDirectoryAsString = FolderHelper.getHomeDirectory(userIDAuth.getUserID()).getValue();
        for (BucketPath encryptedBucketPath : list) {
            BucketPath unecryptedBucketPath = bucketPathEncryptionService.decrypt(dfsAndKeystoreAndPath.pathEncryptionKey, encryptedBucketPath);
            String unecryptedBucketPathAsString = unecryptedBucketPath.getValue();
            if (!unecryptedBucketPathAsString.startsWith(homeDirectoryAsString)) {
                throw new BaseException("ProgrammingError:" + unecryptedBucketPathAsString + " does not start with " + homeDirectoryAsString);
            }
            String documentFQN = unecryptedBucketPathAsString.substring(homeDirectoryAsString.length());
            retList.add(new DocumentFQN(documentFQN));
        }
        return retList;
    }

    @Override
    public List<DocumentFQN> listInbox(UserIDAuth userIDAuth) {
        List<DocumentFQN> retList = new ArrayList<>();
        BucketDirectory inboxDirectory = FolderHelper.getInboxDirectory(userIDAuth.getUserID());
        String inboxDirectoryAsString = inboxDirectory.getValue();
        List<BucketPath> list = systemDFS.list(inboxDirectory, ListRecursiveFlag.TRUE);
        for (BucketPath bucketPath : list) {
            String bucketPathAsString = bucketPath.getValue();
            if (!bucketPathAsString.startsWith(inboxDirectoryAsString)) {
                throw new BaseException("ProgrammingError:" + bucketPathAsString + " does not start with " + inboxDirectoryAsString);
            }
            String documentFQN = bucketPathAsString.substring(inboxDirectoryAsString.length());
            retList.add(new DocumentFQN(documentFQN));
        }
        return retList;
    }

    @Override
    public void writeDocumentToInboxOfUser(UserID receiverUserID, DSDocument document, DocumentFQN destDocumentFQN) {
        Payload payload = encryptDataForUserWithRandomKey(receiverUserID, new SimplePayloadImpl(document.getDocumentContent().getValue()));
        BucketDirectory inboxDirectory = FolderHelper.getInboxDirectory(receiverUserID);
        BucketPath destination = inboxDirectory.appendName(destDocumentFQN.getValue());
        systemDFS.putBlob(destination, payload);
    }

    @Override
    public DSDocument readDocumentFromInbox(UserIDAuth userIDAuth, DocumentFQN source) {
        try {
            BucketDirectory inboxDirectory = FolderHelper.getInboxDirectory(userIDAuth.getUserID());
            BucketPath bucketPath = inboxDirectory.appendName(source.getValue());
            Payload blob = systemDFS.getBlob(bucketPath);
            DFSAndKeystoreAndPath dfsAndKeystoreAndPath = getUsersAccess(userIDAuth, source);
            CMSEnvelopedData cmsEnvelopedData = new CMSEnvelopedData(blob.getData());
            Payload decrypt = cmsEncryptionService.decrypt(cmsEnvelopedData, dfsAndKeystoreAndPath.privateKeystoreAccess);
            return new DSDocument(source, new DocumentContent(decrypt.getData()));
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public void deleteDocumentFromInbox(UserIDAuth userIDAuth, DocumentFQN documentFQN) {
        BucketDirectory inboxDirectory = FolderHelper.getInboxDirectory(userIDAuth.getUserID());
        BucketPath bucketPath = inboxDirectory.appendName(documentFQN.getValue());
        systemDFS.removeBlob(bucketPath);
    }

    @Override
    public void moveDocumnetToInboxOfUser(UserIDAuth userIDAuth, UserID receiverUserID, DocumentFQN sourceDocumentFQN, DocumentFQN destDocumentFQN, MoveType moveType) {
        DSDocument dsDocument = readDocument(userIDAuth, sourceDocumentFQN);
        writeDocumentToInboxOfUser(receiverUserID, dsDocument, destDocumentFQN);
        if (moveType.equals(MoveType.MOVE)) {
            deleteDocument(userIDAuth, sourceDocumentFQN);
        }
    }

    @Override
    public DSDocument moveDocumentFromInbox(UserIDAuth userIDAuth, DocumentFQN source, DocumentFQN destination) {
        DSDocument dsDocument = readDocumentFromInbox(userIDAuth, source);
        DSDocument destDocument = new DSDocument(destination, dsDocument.getDocumentContent());
        storeDocument(userIDAuth, destDocument);
        deleteDocumentFromInbox(userIDAuth, source);
        return destDocument;
    }


    private void persistKeystore(UserIDAuth userIDAuth, KeyStore usersSystemKeyStore, DFSConnection systemDFS) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        usersSystemKeyStore.store(stream, userIDAuth.getReadKeyPassword().getValue().toCharArray());
        Payload payload = new SimplePayloadImpl(stream.toByteArray());
        systemDFS.putBlob(FolderHelper.getKeyStorePath(userIDAuth.getUserID()), payload);
    }

    /* reads the system dfs users key store to decrypt the dfsCredentials.
     * the a dfs connection is created with these dfsCredentials
     */
    private DFSConnection getUsersDFS(UserIDAuth userIDAuth) {
        try {
            DFSCredentials userDFSCredentials = userDFSCredentialsCache.get(new UserAuthCacheKey(userIDAuth));
            if (userDFSCredentials == null) {
                KeyStoreAccess publicKeyStoreAccess = getKeyStoreAccess(systemDFS, userIDAuth);

                {
                    // retrieve DFS
                    Payload encryptedPayload = systemDFS.getBlob(FolderHelper.getDFSCredentialsPath(userIDAuth.getUserID()));
                    CMSEnvelopedData cmsEnvelopedData = new CMSEnvelopedData(encryptedPayload.getData());
                    Payload decrypt = cmsEncryptionService.decrypt(cmsEnvelopedData, publicKeyStoreAccess);
                    userDFSCredentials = class2JsonHelper.contentToDFSConnection(decrypt);
                }
                userDFSCredentialsCache.put(new UserAuthCacheKey(userIDAuth), userDFSCredentials);
            }
            return DFSConnectionFactory.get(userDFSCredentials.getProperties());

        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    /* reads and returns the keystore from the provided dfs
     */
    private KeyStoreAccess getKeyStoreAccess(DFSConnection dfs, UserIDAuth userIDAuth) {
        try {
            KeyStoreAccess keyStoreAccess = null;
            {
                KeyStoreAuth keyStoreAuth = new KeyStoreAuth(new ReadStorePassword(userIDAuth.getReadKeyPassword().getValue()), userIDAuth.getReadKeyPassword());
                Payload payload = dfs.getBlob(FolderHelper.getKeyStorePath(userIDAuth.getUserID()));
                ByteArrayInputStream in = new ByteArrayInputStream(payload.getData());
                KeyStore keystore = KeyStore.getInstance(KeyStoreType.DEFAULT.getValue());
                keystore.load(in, userIDAuth.getReadKeyPassword().getValue().toCharArray());
                keyStoreAccess = new KeyStoreAccess(keystore, keyStoreAuth);
            }
            return keyStoreAccess;
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    private DFSAndKeystoreAndPath getUsersAccess(UserIDAuth userIDAuth) {
        return getUsersAccess(userIDAuth, null, null);
    }

    private DFSAndKeystoreAndPath getUsersAccess(UserIDAuth userIDAuth, DocumentFQN documentFQN) {
        return getUsersAccess(userIDAuth, null, documentFQN);
    }

    private DFSAndKeystoreAndPath getUsersAccess(UserIDAuth userIDAuth, DocumentDirectoryFQN documentDirectoryFQN) {
        return getUsersAccess(userIDAuth, documentDirectoryFQN, null);
    }

    private DFSAndKeystoreAndPath getUsersAccess(UserIDAuth userIDAuth, DocumentDirectoryFQN documentDirectoryFQN, DocumentFQN documentFQN) {
        DFSAndKeystoreAndPath dfsAndKeystoreAndPath = new DFSAndKeystoreAndPath();
        dfsAndKeystoreAndPath.usersDFS = getUsersDFS(userIDAuth);
        KeyStoreAccess privateKeyStoreAccess = usersPrivateKeyStoreCache.get(new UserAuthCacheKey(userIDAuth));
        if (privateKeyStoreAccess == null) {
            privateKeyStoreAccess = getKeyStoreAccess(dfsAndKeystoreAndPath.usersDFS, userIDAuth);
            usersPrivateKeyStoreCache.put(new UserAuthCacheKey(userIDAuth), privateKeyStoreAccess);
        }
        dfsAndKeystoreAndPath.privateKeystoreAccess = privateKeyStoreAccess;

        SecretKey secretKey = userPathSecretKeyCache.get(new UserAuthCacheKey(userIDAuth));
        if (secretKey == null) {
            secretKey = keyStoreService.getRandomSecretKeyID(dfsAndKeystoreAndPath.privateKeystoreAccess).getSecretKey();
            userPathSecretKeyCache.put(new UserAuthCacheKey(userIDAuth), secretKey);
        }
        dfsAndKeystoreAndPath.pathEncryptionKey = secretKey;

        if (documentFQN != null) {
            BucketPath unencryptedPath = FolderHelper.getHomeDirectory(userIDAuth.getUserID()).appendName(documentFQN.getValue());
            dfsAndKeystoreAndPath.encryptedBucketPath = bucketPathEncryptionService.encrypt(dfsAndKeystoreAndPath.pathEncryptionKey, unencryptedPath);
        }
        if (documentDirectoryFQN != null) {
            BucketDirectory unencryptedDirectory = FolderHelper.getHomeDirectory(userIDAuth.getUserID()).appendDirectory(documentDirectoryFQN.getValue());
            dfsAndKeystoreAndPath.encryptedBucketDirectory = bucketPathEncryptionService.encrypt(dfsAndKeystoreAndPath.pathEncryptionKey, unencryptedDirectory);
        }
        return dfsAndKeystoreAndPath;
    }


    private final static class DFSAndKeystoreAndPath {
        DFSConnection usersDFS;
        KeyStoreAccess privateKeystoreAccess;
        SecretKey pathEncryptionKey;
        BucketPath encryptedBucketPath;
        BucketDirectory encryptedBucketDirectory;

    }

    private Payload encryptDataForUserWithRandomKey(UserID userID, Payload unencryptedContent) {
        try {
            PublicKeyIDWithPublicKey publicKeyIDWithPublicKey = getPublicKeyIDWithPublicKey(userID);
            Payload unencryptedPayload = new SimplePayloadImpl(unencryptedContent.getData());
            CMSEnvelopedData cmsEnvelope = cmsEncryptionService.encrypt(unencryptedPayload, publicKeyIDWithPublicKey.getPublicKey(), publicKeyIDWithPublicKey.getKeyID());
            return new SimplePayloadImpl(cmsEnvelope.getEncoded());
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    private PublicKeyIDWithPublicKey getPublicKeyIDWithPublicKey(UserID userID) {
        PublicKeyList publicKeyIDWithPublicKeys = userPublicKeyListCache.get(userID);
        if (publicKeyIDWithPublicKeys == null) {
            Payload publicKeysAsPayload = systemDFS.getBlob(FolderHelper.getPublicKeyListPath(userID));
            List<PublicKeyIDWithPublicKey> publicKeys = class2JsonHelper.contentToKeyList(publicKeysAsPayload);
            publicKeyIDWithPublicKeys = new PublicKeyList(publicKeys);
            userPublicKeyListCache.put(userID, publicKeyIDWithPublicKeys);
        }
        // get random public key to encrypt
        Random random = new Random();
        return publicKeyIDWithPublicKeys.get(random.nextInt(publicKeyIDWithPublicKeys.size()));
    }

    private void storeUserDFSCredentials(UserIDAuth userIDAuth, KeyStoreAccess publicKeyStoreAccess, DFSCredentials userDFSCredentials) throws IOException {
        PublicKeyIDWithPublicKey publicKeyIDWithPublicKey = keyStoreService.getPublicKeys(publicKeyStoreAccess).get(0);
        Payload payload = class2JsonHelper.dfsCredentialsToContent(userDFSCredentials);
        CMSEnvelopedData encryptedDFSCredentialsAsEnvelope = cmsEncryptionService.encrypt(payload, publicKeyIDWithPublicKey.getPublicKey(), publicKeyIDWithPublicKey.getKeyID());
        Payload encryptedPayload = new SimplePayloadImpl(encryptedDFSCredentialsAsEnvelope.getEncoded());
        systemDFS.putBlob(FolderHelper.getDFSCredentialsPath(userIDAuth.getUserID()), encryptedPayload);
        log.debug("stored the new dfs credentials info");
    }
}
