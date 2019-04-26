package org.adorsys.docusafe.service.bucketpathencryption;

import de.adorsys.dfs.connection.api.complextypes.BucketDirectory;
import de.adorsys.dfs.connection.api.complextypes.BucketPath;
import de.adorsys.dfs.connection.api.complextypes.BucketPathUtil;
import lombok.extern.slf4j.Slf4j;
import org.adorsys.docusafe.service.api.bucketpathencryption.BucketPathEncryptionService;
import org.adorsys.docusafe.service.api.keystore.KeyStoreService;
import org.adorsys.docusafe.service.api.keystore.types.*;
import org.adorsys.docusafe.service.impl.bucketpathencryption.BucketPathEncryptionServiceImpl;
import org.adorsys.docusafe.service.impl.keystore.service.KeyStoreServiceImpl;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyStore;
import java.util.Date;


@Slf4j
public class BucketPathEncryptionTest {

    @Test
    public void encryptionTest() {
        BucketPathEncryptionService bucketPathEncryptionService = new BucketPathEncryptionServiceImpl();
        SecretKey secretKey = getSecretKey();

        BucketPath bucketPath = new BucketPath("/folder1/folder2/folder3/file1.txt");
        int loopsize = 100;
        {
            long start = new Date().getTime();
            for (int i = 0; i < loopsize; i++) {
                BucketPath encryptedBucketPath = bucketPathEncryptionService.encrypt(secretKey, bucketPath);
                BucketPath decryptedBucketPath = bucketPathEncryptionService.decrypt(secretKey, encryptedBucketPath);
                Assert.assertEquals(decryptedBucketPath.toString(),bucketPath.toString());
            }
            long stop = new Date().getTime();
            BucketPath encryptedBucketPath = bucketPathEncryptionService.encrypt(secretKey, bucketPath);

            log.info(String.format("asymmetric encryption of \"%s\" for %d times took time: %d ms", bucketPath, loopsize, (stop - start)));
            log.info(String.format("asymmetric encryption blew up path length from %d to %d ", BucketPathUtil.getAsString(bucketPath).length(), BucketPathUtil.getAsString(encryptedBucketPath).length()));
        }

    }

    @Test
    public void encryptionPartTest() {
        BucketPathEncryptionService bucketPathEncryptionService = new BucketPathEncryptionServiceImpl();
        SecretKey secretKey = getSecretKey();

        BucketPath bucketPath1 = new BucketPath("/folder1/folder2/folder3/file1.txt");
        BucketPath bucketPath2 = bucketPath1.getBucketDirectory().appendName("anotherfile");
        BucketPath full1 = bucketPathEncryptionService.encrypt(secretKey, bucketPath1);
        BucketPath full2 = bucketPathEncryptionService.encrypt(secretKey, bucketPath2);
        BucketDirectory d1 = full1.getBucketDirectory();
        BucketDirectory d2 = full2.getBucketDirectory();

        Assert.assertEquals(BucketPathUtil.getAsString(d2),BucketPathUtil.getAsString(d1));
        log.info(bucketPath1 + " and " + bucketPath2 + " both have the same prefix when encrypted:" + d1);
    }

    private SecretKey getSecretKey() {
        KeyStoreService keyStoreService = new KeyStoreServiceImpl();
        ReadKeyPassword readKeyPassword = new ReadKeyPassword("readkeypassword");
        ReadStorePassword readStorePassword = new ReadStorePassword("readstorepassword");
        KeyStoreAuth keyStoreAuth = new KeyStoreAuth(readStorePassword, readKeyPassword);
        KeyStoreCreationConfig config = new KeyStoreCreationConfig(0, 0, 1);
        KeyStore keyStore = keyStoreService.createKeyStore(keyStoreAuth, KeyStoreType.DEFAULT, config);
        KeyStoreAccess keyStoreAccess = new KeyStoreAccess(keyStore, keyStoreAuth);
        SecretKeyIDWithKey randomSecretKeyIDWithKey = keyStoreService.getRandomSecretKeyID(keyStoreAccess);
        return randomSecretKeyIDWithKey.getSecretKey();
    }
}
