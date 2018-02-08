package org.adorsys.docusafe.business.types.complex;

import org.adorsys.encobject.domain.UserMetaData;

/**
 * Created by peter on 23.01.18 at 18:47.
 */
public class DSDocumentMetaInfo extends UserMetaData {
    public DSDocumentMetaInfo() {

    }
    public DSDocumentMetaInfo(UserMetaData otherUserMetadata) {
        if (otherUserMetadata != null) {
            for (String key : otherUserMetadata.keySet()) {
                put(key, otherUserMetadata.get(key));
            }
        }
    }
}