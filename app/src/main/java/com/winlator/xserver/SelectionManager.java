package com.winlator.xserver;

import android.util.SparseArray;

import com.winlator.xserver.events.SelectionClear;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Who owns each selection. Besides X clients, the server itself can own CLIPBOARD with text from Android (the
 * Android clipboard, or a long paste from the keyboard), so Wine's paste (Ctrl+V) in the game gets it.
 */
public class SelectionManager implements XResourceManager.OnResourceLifecycleListener {
    private final SparseArray<Selection> selections = new SparseArray<>();
    private String serverText;
    private int serverTextVersion;

    public SelectionManager(WindowManager windowManager) {
        windowManager.addOnResourceLifecycleListener(this);
    }

    public static class Selection {
        public Window owner;
        /** The owning client, or null when the server owns the selection. */
        private XClient client;

        public XClient ownerClient() {
            return client;
        }

        public boolean ownedByServer() {
            return owner != null && client == null;
        }
    }

    public void setSelection(int atom, Window owner, XClient client, int timestamp) {
        Selection selection = getSelection(atom);
        // The previous owner learns it lost the selection, unless it's the server or the same client taking it again.
        if (selection.owner != null && selection.client != null && selection.client != client) {
            selection.client.sendEvent(new SelectionClear(timestamp, selection.owner, atom));
        }
        selection.owner = owner;
        selection.client = owner != null ? client : null;
    }

    public Selection getSelection(int atom) {
        Selection selection = selections.get(atom);
        if (selection != null) return selection;
        selection = new Selection();
        selections.put(atom, selection);
        return selection;
    }

    /** Makes the server own CLIPBOARD with [text], owned through [root]. A client that owned it gets SelectionClear. */
    public void offerClipboardText(String text, Window root) {
        if (text == null || text.isEmpty()) return;
        serverText = text;
        serverTextVersion++;
        setSelection(clipboardAtom(), root, null, (int)System.currentTimeMillis());
    }

    /**
     * Converts a server-owned selection to [target] into [property] on [requestor]. Returns the property it set,
     * or 0 when it can't convert (not owned by the server, no text, or a target it doesn't offer).
     */
    public int convertServerSelection(int selection, int target, int property, Window requestor) {
        Selection owned = getSelection(selection);
        if (!owned.ownedByServer() || selection != clipboardAtom() || serverText == null) return 0;
        String name = Atom.getName(target);
        if (name == null) return 0;
        int type;
        Property.Format format = Property.Format.BYTE_ARRAY;
        byte[] data;
        switch (name) {
            case "TARGETS": {
                type = Atom.getId("ATOM");
                format = Property.Format.INT_ARRAY;
                int utf8 = Atom.internAtom("UTF8_STRING");
                int plain = Atom.internAtom("text/plain;charset=utf-8");
                int string = Atom.getId("STRING");
                // Wine polls the owner and only re-reads the clipboard when the owner or this list changes. The owner
                // stays the root window, so the order alternates with each new text to show Wine it changed.
                int[] targets = (serverTextVersion & 1) == 0
                    ? new int[] {target, utf8, plain, string}
                    : new int[] {target, string, plain, utf8};
                ByteBuffer buffer = ByteBuffer.allocate(targets.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int atom : targets) buffer.putInt(atom);
                data = buffer.array();
                break;
            }
            case "UTF8_STRING":
            case "text/plain;charset=utf-8":
                type = target;
                data = serverText.getBytes(StandardCharsets.UTF_8);
                break;
            case "STRING":
                type = target;
                data = latin1(serverText);
                break;
            default:
                return 0;
        }
        if (requestor.modifyProperty(property, type, format, Property.Mode.REPLACE, data) == null) return 0;
        return property;
    }

    private static byte[] latin1(String text) {
        CharsetEncoder encoder = XServer.LATIN1_CHARSET.newEncoder()
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .onMalformedInput(CodingErrorAction.REPLACE);
        try {
            ByteBuffer encoded = encoder.encode(java.nio.CharBuffer.wrap(text));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (java.nio.charset.CharacterCodingException e) {
            return new byte[0];
        }
    }

    private static int clipboardAtom() {
        return Atom.internAtom("CLIPBOARD");
    }

    @Override
    public void onFreeResource(XResource resource) {
        for (int i = 0; i < selections.size(); i++) {
            Selection selection = selections.valueAt(i);
            if (selection.owner == resource) {
                selection.owner = null;
                selection.client = null;
            }
        }
    }
}
