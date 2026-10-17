package com.example.ClaimShieldX.documents.service;

import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.exceptions.InvalidStateTransitionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The one short database transaction of an upload: the new row and, for a revision, the retirement of the old one. */
@Component
class DocumentPersistence {
    private final DocumentRepository documentRepository;

    DocumentPersistence(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    Document persist(Document document, UUID supersedesDocumentId) {
        Document saved = documentRepository.saveAndFlush(document);
        if (supersedesDocumentId != null && documentRepository.markSuperseded(supersedesDocumentId) == 0) {
            throw new InvalidStateTransitionException("The document was already replaced");
        }
        return saved;
    }
}
