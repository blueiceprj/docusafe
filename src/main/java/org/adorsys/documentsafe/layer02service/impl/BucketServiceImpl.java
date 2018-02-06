package org.adorsys.documentsafe.layer02service.impl;

import org.adorsys.cryptoutils.exceptions.BaseExceptionHandler;
import org.adorsys.documentsafe.layer02service.BucketService;
import org.adorsys.documentsafe.layer02service.types.PlainFileContent;
import org.adorsys.documentsafe.layer02service.types.complextypes.BucketContent;
import org.adorsys.encobject.complextypes.BucketPath;
import org.adorsys.encobject.service.ContainerPersistence;
import org.adorsys.encobject.service.ExtendedStoreConnection;
import org.adorsys.encobject.service.StoreConnection;
import org.adorsys.encobject.types.ListRecursiveFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by peter on 17.01.18 at 16:44.
 */
public class BucketServiceImpl implements BucketService {
    private final static Logger LOGGER = LoggerFactory.getLogger(BucketServiceImpl.class);
    private ContainerPersistence containerPersistence;
    private ExtendedStoreConnection extendedStoreConnection;

    public BucketServiceImpl(ExtendedStoreConnection extendedStoreConnection) {
        this.extendedStoreConnection = extendedStoreConnection;
        this.containerPersistence = new ContainerPersistence(this.extendedStoreConnection);
    }

    @Override
    public void createBucket(BucketPath bucketPath) {
        try {
            containerPersistence.creteContainer(bucketPath.getObjectHandle().getContainer());
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public void destroyBucket(BucketPath bucketPath) {
        try {
            containerPersistence.deleteContainer(bucketPath.getObjectHandle().getContainer());
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public BucketContent readDocumentBucket(BucketPath bucketPath, ListRecursiveFlag listRecursiveFlag) {
        LOGGER.info("start read document bucket " + bucketPath);
        BucketContent bucketContent = new BucketContent(bucketPath, extendedStoreConnection.list(bucketPath, listRecursiveFlag));
        LOGGER.info("finished read document bucket " + bucketPath + " -> " + bucketContent.getOriginalContent().size());
        return bucketContent;
    }

    @Override
    public boolean bucketExists(BucketPath bucketPath) {
        LOGGER.info("start check bucket exsits " + bucketPath);
        boolean b = extendedStoreConnection.containerExists(bucketPath.getObjectHandle().getContainer());
        LOGGER.info("finished check bucket exsits " + bucketPath + " -> " + b);
        return b;
    }

    @Override
    public void createPlainFile(BucketPath bucketPath, PlainFileContent plainFileContent) {
        try {
            LOGGER.info("start create plain file " + bucketPath);
            extendedStoreConnection.putBlob(bucketPath.getObjectHandle(), plainFileContent.getValue());
            LOGGER.info("finished create plain file " + bucketPath);
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public void deletePlainFile(BucketPath bucketPath) {
        try {
            LOGGER.info("start delete plain file " + bucketPath);
            extendedStoreConnection.removeBlob(bucketPath);
            LOGGER.info("finished delete plain file " + bucketPath);
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public PlainFileContent readPlainFile(BucketPath bucketPath) {
        try {
            LOGGER.info("start read plain file " + bucketPath);
            PlainFileContent plainFileContent = new PlainFileContent(extendedStoreConnection.getBlob(bucketPath.getObjectHandle()));
            LOGGER.info("finished read plain file " + bucketPath);
            return plainFileContent;
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public boolean existsFile(BucketPath bucketPath) {
        LOGGER.info("start file exists " + bucketPath);
        boolean blobExists = extendedStoreConnection.blobExists(bucketPath.getObjectHandle());
        LOGGER.info("finished file exists " + bucketPath);
        return blobExists;
    }

}
