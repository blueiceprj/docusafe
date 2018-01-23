package org.adorsys.documentsafe.layer04rest.types;

import org.adorsys.documentsafe.layer02service.types.DocumentContent;
import org.adorsys.documentsafe.layer03business.types.complex.DocumentFQN;

/**
 * Created by peter on 23.01.18 at 20:08.
 */
public class CreateLinkTupel {
    private DocumentFQN source;
    private DocumentFQN destination;

    public CreateLinkTupel() {
    }

    public CreateLinkTupel(DocumentFQN source, DocumentFQN destination) {
        this.source = source;
        this.destination = destination;
    }

    public DocumentFQN getSource() {
        return source;
    }

    public DocumentFQN getDestination() {
        return destination;
    }
}
