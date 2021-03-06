package de.adorsys.docusafe.business;

import de.adorsys.dfs.connection.api.types.ListRecursiveFlag;
import de.adorsys.docusafe.business.types.*;
import de.adorsys.docusafe.service.api.types.UserID;
import de.adorsys.docusafe.service.api.types.UserIDAuth;

import java.util.List;

/**
 * Created by peter on 19.01.18 at 16:30.
 */
public interface DocumentSafeService {
    /**
     * User
     */
    void createUser(UserIDAuth userIDAuth);

    void destroyUser(UserIDAuth userIDAuth);

    boolean userExists(UserID userID);

    void registerDFSCredentials (UserIDAuth userIDAuth, DFSCredentials dfsCredentials);
    /**
     * Document
     */
    void storeDocument(UserIDAuth userIDAuth, DSDocument dsDocument);

    DSDocument readDocument(UserIDAuth userIDAuth, DocumentFQN documentFQN);

    void storeDocumentStream(UserIDAuth userIDAuth, DSDocumentStream dsDocumentStream);

    DSDocumentStream readDocumentStream(UserIDAuth userIDAuth, DocumentFQN documentFQN);

    void deleteDocument(UserIDAuth userIDAuth, DocumentFQN documentFQN);

    boolean documentExists(UserIDAuth userIDAuth, DocumentFQN documentFQN);

    void deleteFolder(UserIDAuth userIDAuth, DocumentDirectoryFQN documentDirectoryFQN);

    List<DocumentFQN> list(UserIDAuth userIDAuth, DocumentDirectoryFQN documentDirectoryFQN, ListRecursiveFlag recursiveFlag);

    /**
     * InboxStuff
     */
    List<DocumentFQN> listInbox(UserIDAuth userIDAuth);

    void writeDocumentToInboxOfUser(UserID receiverUserID, DSDocument document, DocumentFQN destDocumentFQN);

    DSDocument readDocumentFromInbox(UserIDAuth userIDAuth, DocumentFQN source);

    void deleteDocumentFromInbox(UserIDAuth userIDAuth, DocumentFQN documentFQN);

    /**
     * conveniance methods
     */
    void moveDocumnetToInboxOfUser(UserIDAuth userIDAuth, UserID receiverUserID, DocumentFQN sourceDocumentFQN, DocumentFQN destDocumentFQN, MoveType moveType);

    DSDocument moveDocumentFromInbox(UserIDAuth userIDAuth, DocumentFQN source, DocumentFQN destination);
}

