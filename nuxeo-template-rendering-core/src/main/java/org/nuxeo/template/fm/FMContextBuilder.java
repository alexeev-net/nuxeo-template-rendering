package org.nuxeo.template.fm;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.audit.api.DocumentHistoryReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.api.comment.CommentProcessorHelper;
import org.nuxeo.ecm.platform.rendering.fm.adapters.DocumentObjectWrapper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;

public class FMContextBuilder {

    protected static final Log log = LogFactory.getLog(FMContextBuilder.class);

    public static final String[] RESERVED_VAR_NAMES = { "doc", "document",
            "auditEntries", "username" };

    public static List<LogEntry> testAuditEntries;

    public static Map<String, Object> build(DocumentModel doc) throws Exception {
        return build(doc, true);
    }

    public static Map<String, Object> build(DocumentModel doc,
            boolean wrapAuditEntries) throws Exception {

        Map<String, Object> ctx = new HashMap<String, Object>();
        DocumentObjectWrapper nuxeoWrapper = new DocumentObjectWrapper(null);

        ContextFunctions functions = new ContextFunctions(doc, nuxeoWrapper);

        CoreSession session = doc.getCoreSession();

        // doc infos
        ctx.put("doc", nuxeoWrapper.wrap(doc));
        ctx.put("document", nuxeoWrapper.wrap(doc));

        // blob wrapper
        ctx.put("blobHolder", new BlobHolderWrapper(doc));

        // add functions helper
        ctx.put("fn", functions);
        ctx.put("Fn", functions);
        ctx.put("fonctions", functions);

        // user info
        ctx.put("username", session.getPrincipal().getName());
        ctx.put("principal", session.getPrincipal());

        // add audit context info
        DocumentHistoryReader historyReader = Framework.getLocalService(DocumentHistoryReader.class);
        List<LogEntry> auditEntries = null;
        if (historyReader != null) {
            auditEntries = historyReader.getDocumentHistory(doc, 0, 1000);
        } else {
            if (Framework.isTestModeSet() && testAuditEntries != null) {
                auditEntries = testAuditEntries;
            } else {
                log.warn("Can not add Audit info to rendering context");
            }
        }
        if (auditEntries != null) {
            try {
                auditEntries = preprocessAuditEntries(auditEntries, session,
                        "en");
            } catch (Throwable e) {
                log.warn("Unable to preprocess Audit entries : "
                        + e.getMessage());
            }
            if (wrapAuditEntries) {
                ctx.put("auditEntries", nuxeoWrapper.wrap(auditEntries));
            } else {
                ctx.put("auditEntries", auditEntries);
            }
        }
        return ctx;
    }

    protected static List<LogEntry> preprocessAuditEntries(
            List<LogEntry> auditEntries, CoreSession session, String lang) {
        CommentProcessorHelper helper = new CommentProcessorHelper(session);
        for (LogEntry entry : auditEntries) {
            String comment = helper.getLogComment(entry);
            if (comment == null) {
                comment = "";
            } else {
                String i18nComment = I18NUtils.getMessageString("messages",
                        comment, null, new Locale(lang));
                if (i18nComment != null) {
                    comment = i18nComment;
                }
            }
            String eventId = entry.getEventId();
            String i18nEventId = I18NUtils.getMessageString("messages",
                    eventId, null, new Locale(lang));
            if (i18nEventId != null) {
                entry.setEventId(i18nEventId);
            }
            entry.setComment(comment);
        }
        return auditEntries;
    }

    public static Map<String, Object> build(
            TemplateBasedDocument templateBasedDocument, String templateName)
            throws Exception {

        DocumentModel doc = templateBasedDocument.getAdaptedDoc();

        Map<String, Object> context = build(doc);

        return context;
    }

}
