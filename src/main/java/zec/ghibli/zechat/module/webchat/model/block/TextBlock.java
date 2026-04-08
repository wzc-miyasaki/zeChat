package zec.ghibli.zechat.module.webchat.model.block;

public record TextBlock(int id, String text) implements MessageBlock {
    @Override
    public String type() {
        return "TEXT";
    }
}
