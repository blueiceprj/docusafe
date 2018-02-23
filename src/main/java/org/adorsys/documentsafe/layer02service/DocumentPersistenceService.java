package org.adorsys.documentsafe.layer02service;

import org.adorsys.documentsafe.layer02service.types.complextypes.DocumentBucketPath;
import org.adorsys.documentsafe.layer02service.types.DocumentContent;
import org.adorsys.documentsafe.layer02service.types.complextypes.DocumentKeyIDWithKey;
import org.adorsys.encobject.domain.KeyStoreAccess;
import org.adorsys.encobject.domain.Payload;
import org.adorsys.encobject.domain.StorageMetadata;
import org.adorsys.encobject.types.OverwriteFlag;

/**
 * Created by peter on 11.01.18.
 */
public interface DocumentPersistenceService {
    void persistDocument(
            DocumentKeyIDWithKey documentKeyIDWithKey,
            DocumentBucketPath documentBucketPath,
            DocumentContent documentContent,
            OverwriteFlag overwriteFlag,
            StorageMetadata storageMetadata);

    Payload loadDocument(
            KeyStoreAccess keyStoreAccess,
            DocumentBucketPath documentBucketPath);
}
