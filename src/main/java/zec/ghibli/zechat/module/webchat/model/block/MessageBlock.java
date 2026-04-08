package zec.ghibli.zechat.module.webchat.model.block;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextBlock.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ImageBlock.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = FileBlock.class, name = "FILE"),
})
public interface MessageBlock {
    int id();
    String type();
}
