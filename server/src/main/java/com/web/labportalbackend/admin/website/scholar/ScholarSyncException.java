package com.web.labportalbackend.admin.website.scholar;

/** Only fixed, safe codes are logged or sent to the CMS. Never propagate provider response bodies/URLs. */
public class ScholarSyncException extends RuntimeException {
    public ScholarSyncException(String code) { super(code); }
}
