package com.winlator.xserver.events;

import com.winlator.xconnector.XOutputStream;
import com.winlator.xconnector.XStreamLock;

import java.io.IOException;

/** Asks a selection's owner to convert it for [requestor]; the owner answers with a SelectionNotify via SendEvent. */
public class SelectionRequest extends Event {
    private final int timestamp;
    private final int owner;
    private final int requestor;
    private final int selection;
    private final int target;
    private final int property;

    public SelectionRequest(int timestamp, int owner, int requestor, int selection, int target, int property) {
        super(30);
        this.timestamp = timestamp;
        this.owner = owner;
        this.requestor = requestor;
        this.selection = selection;
        this.target = target;
        this.property = property;
    }

    @Override
    public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(code);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(sequenceNumber);
            outputStream.writeInt(timestamp);
            outputStream.writeInt(owner);
            outputStream.writeInt(requestor);
            outputStream.writeInt(selection);
            outputStream.writeInt(target);
            outputStream.writeInt(property);
            outputStream.writePad(4);
        }
    }
}
