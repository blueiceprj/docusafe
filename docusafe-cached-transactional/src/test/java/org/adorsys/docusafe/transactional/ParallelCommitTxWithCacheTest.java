package org.adorsys.docusafe.transactional;

import org.adorsys.docusafe.cached.transactional.impl.CachedTransactionalFileStorageImpl;
import org.adorsys.docusafe.transactional.impl.TransactionalFileStorageImpl;
import org.junit.Before;

/**
 * Created by peter on 12.07.18 at 11:57.
 */
public class ParallelCommitTxWithCacheTest extends ParallelCommitTxTest {
    @Before
    public void preTest() {
        TransactionalFileStorage localTransactionalFileStorage = new TransactionalFileStorageImpl(requestMemoryContext, dssi);
        this.transactionalFileStorage = new CachedTransactionalFileStorageImpl(requestMemoryContext, localTransactionalFileStorage);
    }

}