package zec.ghibli.zechat.module.webchat.model.block;

public record ImageBlock(int id, String fileId, String mimeType, String inlineData) implements MessageBlock {
    @Override
    public String type() {
        return "IMAGE";
    }
}
