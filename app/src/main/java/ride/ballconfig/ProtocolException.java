package ride.ballconfig;

import proto.Protocol;

class ProtocolException extends  Exception {
    public final Protocol.ReplyId replyId;

    public ProtocolException(Protocol.ReplyId replyId) {
        this.replyId = replyId;
    }

    @Override
    public String toString() {
        return super.toString() + this.replyId.toString();
    }
}
