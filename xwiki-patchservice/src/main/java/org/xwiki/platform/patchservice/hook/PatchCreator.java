package org.xwiki.platform.patchservice.hook;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.suigeneris.jrcs.diff.DifferentiationFailedException;
import org.suigeneris.jrcs.diff.delta.AddDelta;
import org.suigeneris.jrcs.diff.delta.ChangeDelta;
import org.suigeneris.jrcs.diff.delta.DeleteDelta;
import org.suigeneris.jrcs.diff.delta.Delta;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.platform.patchservice.api.Operation;
import org.xwiki.platform.patchservice.api.Patch;
import org.xwiki.platform.patchservice.api.Position;
import org.xwiki.platform.patchservice.api.RWOperation;
import org.xwiki.platform.patchservice.api.RWPatch;
import org.xwiki.platform.patchservice.impl.OperationFactoryImpl;
import org.xwiki.platform.patchservice.impl.PatchImpl;
import org.xwiki.platform.patchservice.impl.PositionImpl;
import org.xwiki.platform.patchservice.plugin.PatchservicePlugin;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.AttachmentDiff;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.ObjectDiff;
import com.xpn.xwiki.objects.classes.PropertyClass;

public class PatchCreator implements EventListener,
    org.xwiki.platform.patchservice.api.PatchCreator
{
    PatchservicePlugin plugin = null;

    public PatchCreator()
    {
    }

    public PatchCreator(PatchservicePlugin plugin)
    {
        this.plugin = plugin;
    }

    /**
     * Event listener method, listens for document changes, and creates the corresponding patches.
     * {@inheritDoc}
     */
    public void onEvent(Event e, Object source, Object data)
    {
        XWikiDocument doc = (XWikiDocument) source;
        Patch p = getPatch(doc, doc.getOriginalDocument(), (XWikiContext) data);
        plugin.getStorage().storePatch(p);
    }

    public Patch getPatch(XWikiDocument oldDoc, XWikiDocument newDoc, XWikiContext context)
    {
        PatchImpl patch = new PatchImpl();
        try {
            getContentChanges(oldDoc, newDoc, patch, context);
            getPropertyChanges(oldDoc, newDoc, patch, context);
            getClassChanges(oldDoc, newDoc, patch, context);
            getObjectChanges(oldDoc, newDoc, patch, context);
            getAttachmentChanges(oldDoc, newDoc, patch, context);
        } catch (DifferentiationFailedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (XWikiException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return patch;
    }

    private void getContentChanges(XWikiDocument oldDoc, XWikiDocument newDoc, RWPatch patch,
        XWikiContext context) throws DifferentiationFailedException, XWikiException
    {
        List<Delta> contentChanges = newDoc.getContentDiff(oldDoc, newDoc, context);
        for (Delta change : contentChanges) {
            if (change instanceof ChangeDelta) {
                RWOperation delete =
                    OperationFactoryImpl.getInstance()
                        .newOperation(Operation.TYPE_CONTENT_DELETE);
                String deletedText =
                    StringUtils.join(change.getOriginal().chunk().iterator(), "\n");
                Position p = new PositionImpl(change.getRevised().first(), 0);
                delete.delete(deletedText, p);
                patch.addOperation(delete);
                RWOperation insert =
                    OperationFactoryImpl.getInstance()
                        .newOperation(Operation.TYPE_CONTENT_INSERT);
                String insertedText =
                    StringUtils.join(change.getRevised().chunk().iterator(), "\n");
                p = new PositionImpl(change.getRevised().first(), 0);
                insert.insert(insertedText, p);
                patch.addOperation(insert);
            } else if (change instanceof AddDelta) {
                RWOperation insert =
                    OperationFactoryImpl.getInstance()
                        .newOperation(Operation.TYPE_CONTENT_INSERT);
                String insertedText =
                    StringUtils.join(change.getRevised().chunk().iterator(), "\n");
                Position p = new PositionImpl(change.getRevised().first(), 0);
                insert.insert(insertedText, p);
                patch.addOperation(insert);
            } else if (change instanceof DeleteDelta) {
                RWOperation delete =
                    OperationFactoryImpl.getInstance()
                        .newOperation(Operation.TYPE_CONTENT_DELETE);
                String deletedText =
                    StringUtils.join(change.getOriginal().chunk().iterator(), "\n");
                Position p = new PositionImpl(change.getRevised().first(), 0);
                delete.delete(deletedText, p);
                patch.addOperation(delete);
            }
        }

        // suigeneris-jrcs does not take into account newline at the end of file, so it must be
        // manually removed or added.
        String oldContent = oldDoc.getContent();
        String newContent = newDoc.getContent();
        if (oldContent.charAt(oldContent.length() - 1) == '\n'
            && newContent.charAt(newContent.length() - 1) != '\n') {
            RWOperation delete =
                OperationFactoryImpl.getInstance().newOperation(Operation.TYPE_CONTENT_DELETE);
            String[] lines = newContent.split("\n");
            Position p = new PositionImpl(lines.length - 1, lines[lines.length - 1].length());
            delete.delete("\n", p);
            patch.addOperation(delete);
        } else if (oldContent.charAt(oldContent.length() - 1) != '\n'
            && newContent.charAt(newContent.length() - 1) == '\n') {
            RWOperation insert =
                OperationFactoryImpl.getInstance().newOperation(Operation.TYPE_CONTENT_INSERT);
            String[] lines = newContent.split("\n");
            Position p = new PositionImpl(lines.length - 1, lines[lines.length - 1].length());
            insert.insert("\n", p);
            patch.addOperation(insert);
        }
    }

    private void getPropertyChanges(XWikiDocument oldDoc, XWikiDocument newDoc, RWPatch patch,
        XWikiContext context) throws DifferentiationFailedException, XWikiException
    {
        if (!newDoc.getCreator().equals(oldDoc.getCreator())) {
            patch.addOperation(getPropertyOperation("creator", newDoc.getCreator()));
        }
        if (!newDoc.getAuthor().equals(oldDoc.getAuthor())) {
            patch.addOperation(getPropertyOperation("author", newDoc.getAuthor()));
        }
        if (!newDoc.getContentAuthor().equals(oldDoc.getContentAuthor())) {
            patch.addOperation(getPropertyOperation("contentAuthor", newDoc.getContentAuthor()));
        }
        if (!newDoc.getCreationDate().equals(oldDoc.getCreationDate())) {
            patch.addOperation(getPropertyOperation("creationDate", Patch.DATE_FORMAT
                .format(newDoc.getCreationDate())));
        }
        if (!newDoc.getDate().equals(oldDoc.getDate())) {
            patch.addOperation(getPropertyOperation("date", Patch.DATE_FORMAT.format(newDoc
                .getDate())));
        }
        if (newDoc.getTranslation() != oldDoc.getTranslation()) {
            patch.addOperation(getPropertyOperation("translation", newDoc.getTranslation() + ""));
        }
        if (!newDoc.getComment().equals(oldDoc.getComment())) {
            patch.addOperation(getPropertyOperation("comment", newDoc.getComment()));
        }
        if (!newDoc.getCustomClass().equals(oldDoc.getCustomClass())) {
            patch.addOperation(getPropertyOperation("customClass", newDoc.getCustomClass()));
        }
        if (!newDoc.getDefaultLanguage().equals(oldDoc.getDefaultLanguage())) {
            patch.addOperation(getPropertyOperation("defaultLanguage", newDoc
                .getDefaultLanguage()));
        }
        if (!newDoc.getLanguage().equals(oldDoc.getLanguage())) {
            patch.addOperation(getPropertyOperation("language", newDoc.getLanguage()));
        }
        if (!newDoc.getDefaultTemplate().equals(oldDoc.getDefaultTemplate())) {
            patch.addOperation(getPropertyOperation("defaultTemplate", newDoc
                .getDefaultTemplate()));
        }
        if (!newDoc.getParent().equals(oldDoc.getParent())) {
            patch.addOperation(getPropertyOperation("parent", newDoc.getParent()));
        }
        if (!newDoc.getTemplate().equals(oldDoc.getTemplate())) {
            patch.addOperation(getPropertyOperation("template", newDoc.getTemplate()));
        }
        if (!newDoc.getTitle().equals(oldDoc.getTitle())) {
            patch.addOperation(getPropertyOperation("title", newDoc.getTitle()));
        }
        if (!newDoc.getValidationScript().equals(oldDoc.getValidationScript())) {
            patch.addOperation(getPropertyOperation("validationScript", newDoc
                .getValidationScript()));
        }
    }

    private void getClassChanges(XWikiDocument oldDoc, XWikiDocument newDoc, RWPatch patch,
        XWikiContext context) throws DifferentiationFailedException, XWikiException
    {
        List<List<ObjectDiff>> classesChanged = newDoc.getClassDiff(oldDoc, newDoc, context);
        for (List<ObjectDiff> classChanges : classesChanged) {
            for (ObjectDiff diff : classChanges) {
                if ("added".equals(diff.getAction())) {
                    RWOperation operation =
                        OperationFactoryImpl.getInstance().newOperation(
                            Operation.TYPE_CLASS_PROPERTY_ADD);
                    PropertyClass property =
                        (PropertyClass) newDoc.getxWikiClass().get(diff.getPropName());
                    Map<String, Object> config = new HashMap<String, Object>();
                    for (Iterator it3 = property.getFieldList().iterator(); it3.hasNext();) {
                        BaseProperty pr = (BaseProperty) it3.next();
                        config.put(pr.getName(), pr.getValue());
                    }
                    operation.createType(newDoc.getFullName(), diff.getPropName(), property
                        .getClassName(), config);
                    patch.addOperation(operation);
                } else if ("changed".equals(diff.getAction())) {
                    RWOperation operation =
                        OperationFactoryImpl.getInstance().newOperation(
                            Operation.TYPE_CLASS_PROPERTY_CHANGE);
                    PropertyClass property =
                        (PropertyClass) newDoc.getxWikiClass().get(diff.getPropName());
                    Map<String, Object> config = new HashMap<String, Object>();
                    for (Iterator it3 = property.getFieldList().iterator(); it3.hasNext();) {
                        BaseProperty pr = (BaseProperty) it3.next();
                        config.put(pr.getName(), pr.getValue());
                    }
                    operation.modifyType(newDoc.getFullName(), diff.getPropName(), config);
                    patch.addOperation(operation);
                } else if ("removed".equals(diff.getAction())) {
                    RWOperation operation =
                        OperationFactoryImpl.getInstance().newOperation(
                            Operation.TYPE_CLASS_PROPERTY_DELETE);
                    operation.deleteType(newDoc.getFullName(), diff.getPropName());
                    patch.addOperation(operation);
                }
            }
        }
    }

    private void getObjectChanges(XWikiDocument oldDoc, XWikiDocument newDoc, RWPatch patch,
        XWikiContext context) throws DifferentiationFailedException, XWikiException
    {
        List<List<ObjectDiff>> objectClassesChanged =
            newDoc.getObjectDiff(oldDoc, newDoc, context);
        for (List<ObjectDiff> classChanges : objectClassesChanged) {
            for (ObjectDiff diff : classChanges) {
                if ("object-added".equals(diff.getAction())) {
                    RWOperation operation =
                        OperationFactoryImpl.getInstance()
                            .newOperation(Operation.TYPE_OBJECT_ADD);
                    operation.addObject(diff.getClassName());
                    patch.addOperation(operation);
                } else if ("object-removed".equals(diff.getAction())) {
                    RWOperation operation =
                        OperationFactoryImpl.getInstance().newOperation(
                            Operation.TYPE_OBJECT_DELETE);
                    operation.deleteObject(diff.getClassName(), diff.getNumber());
                    patch.addOperation(operation);
                    break;
                } else {
                    RWOperation operation =
                        OperationFactoryImpl.getInstance().newOperation(
                            Operation.TYPE_OBJECT_PROPERTY_SET);
                    operation.setObjectProperty(diff.getClassName(), diff.getNumber(), diff
                        .getPropName(), (String) diff.getNewValue());
                    patch.addOperation(operation);
                }
            }
        }
    }

    private void getAttachmentChanges(XWikiDocument oldDoc, XWikiDocument newDoc, RWPatch patch,
        XWikiContext context) throws DifferentiationFailedException, XWikiException
    {
        List<AttachmentDiff> attachmentsChanged =
            newDoc.getAttachmentDiff(oldDoc, newDoc, context);
        for (AttachmentDiff diff : attachmentsChanged) {
            if (diff.getOrigVersion() == null) {
                // Added attachment
                RWOperation operation =
                    OperationFactoryImpl.getInstance()
                        .newOperation(Operation.TYPE_ATTACHMENT_ADD);
                XWikiAttachment attachment = newDoc.getAttachment(diff.getFileName());
                operation.addAttachment(new ByteArrayInputStream(attachment.getContent(context)),
                    attachment.getFilename(), attachment.getAuthor());
                patch.addOperation(operation);
            } else if (diff.getNewVersion() == null) {
                // Deleted attachment
                RWOperation operation =
                    OperationFactoryImpl.getInstance().newOperation(
                        Operation.TYPE_ATTACHMENT_DELETE);
                operation.deleteAttachment(diff.getFileName());
                patch.addOperation(operation);
            } else {
                // Updated attachment
                RWOperation operation =
                    OperationFactoryImpl.getInstance()
                        .newOperation(Operation.TYPE_ATTACHMENT_SET);
                XWikiAttachment attachment = newDoc.getAttachment(diff.getFileName());
                operation.setAttachment(new ByteArrayInputStream(attachment.getContent(context)),
                    attachment.getFilename(), attachment.getAuthor());
                patch.addOperation(operation);
            }
        }
    }

    private Operation getPropertyOperation(String propertyName, String propertyValue)
        throws XWikiException
    {
        RWOperation operation;
        operation = OperationFactoryImpl.getInstance().newOperation(Operation.TYPE_PROPERTY_SET);
        operation.setProperty(propertyName, propertyValue);
        return operation;
    }
}
