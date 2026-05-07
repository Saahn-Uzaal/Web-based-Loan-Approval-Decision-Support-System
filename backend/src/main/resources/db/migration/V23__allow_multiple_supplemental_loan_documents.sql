ALTER TABLE loan_request_documents
    MODIFY COLUMN document_type ENUM(
        'VEHICLE_REGISTRATION',
        'LICENSE_PLATE_IMAGE',
        'ID_CARD_FRONT',
        'ID_CARD_BACK',
        'FACE_CAPTURE',
        'SUPPLEMENTAL_DOCUMENT'
    ) NOT NULL;

CREATE INDEX idx_loan_request_documents_loan_request
ON loan_request_documents(loan_request_id);

DROP INDEX idx_loan_request_documents_loan_type ON loan_request_documents;

CREATE INDEX idx_loan_request_documents_loan_type
ON loan_request_documents(loan_request_id, document_type);

DROP INDEX idx_loan_request_documents_loan_request ON loan_request_documents;
