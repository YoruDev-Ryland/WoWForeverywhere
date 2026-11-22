package com.winlator.xserver.requests;

import static com.winlator.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.xconnector.XInputStream;
import com.winlator.xconnector.XOutputStream;
import com.winlator.xconnector.XStreamLock;
import com.winlator.xserver.Atom;
import com.winlator.xserver.Window;
import com.winlator.xserver.SelectionManager;
import com.winlator.xserver.XClient;
import com.winlator.xserver.events.SelectionNotify;
import com.winlator.xserver.events.SelectionRequest;
import com.winlator.xserver.errors.BadAtom;
import com.winlator.xserver.errors.BadWindow;
import com.winlator.xserver.errors.XRequestError;

import java.io.IOException;

public abstract class SelectionRequests {
    public static void setSelectionOwner(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int atom = inputStream.readInt();
        int timestamp = inputStream.readInt();

        Window owner = client.xServer.windowManager.getWindow(windowId);
        if (owner == null) throw new BadWindow(windowId);
        if (!Atom.isValid(atom)) throw new BadAtom(atom);

        client.xServer.selectionManager.setSelection(atom, owner, client, timestamp);
    }

    public static void getSelectionOwner(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int atom = inputStream.readInt();
        if (!Atom.isValid(atom)) throw new BadAtom(atom);
        Window owner = client.xServer.selectionManager.getSelection(atom).owner;

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(owner != null ? owner.id : 0);
            outputStream.writePad(20);
        }
    }

    /**
     * Converts a selection for its requestor: a client owner gets a SelectionRequest and answers itself; a selection
     * the server owns (Android text on CLIPBOARD) is written to the requestor's property here. Otherwise the requestor
     * is told no with property 0.
     */
    public static void convertSelection(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int requestorId = inputStream.readInt();
        int selectionAtom = inputStream.readInt();
        int target = inputStream.readInt();
        int property = inputStream.readInt();
        int timestamp = inputStream.readInt();

        Window requestor = client.xServer.windowManager.getWindow(requestorId);
        if (requestor == null) throw new BadWindow(requestorId);
        if (!Atom.isValid(selectionAtom)) throw new BadAtom(selectionAtom);
        if (!Atom.isValid(target)) throw new BadAtom(target);
        if (property != 0 && !Atom.isValid(property)) throw new BadAtom(property);

        SelectionManager selections = client.xServer.selectionManager;
        SelectionManager.Selection selection = selections.getSelection(selectionAtom);
        XClient ownerClient = selection.ownerClient();
        if (selection.owner != null && ownerClient != null) {
            ownerClient.sendEvent(new SelectionRequest(timestamp, selection.owner.id, requestorId, selectionAtom, target, property));
            return;
        }
        // An obsolete requestor may pass no property; the target names it then (ICCCM).
        int answered = selections.convertServerSelection(selectionAtom, target, property != 0 ? property : target, requestor);
        client.sendEvent(new SelectionNotify(timestamp, requestorId, selectionAtom, target, answered));
    }
}
