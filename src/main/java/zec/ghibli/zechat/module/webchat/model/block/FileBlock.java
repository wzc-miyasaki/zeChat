package zec.ghibli.zechat.module.webchat.model.block;

public record FileBlock(int id, String fileId, String fileName, String mimeType, long size) implements MessageBlock {
    @Override
    public String type() {
        return "FILE";
    }
}
