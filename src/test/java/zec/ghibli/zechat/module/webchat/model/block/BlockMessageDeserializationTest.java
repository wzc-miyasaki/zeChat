package zec.ghibli.zechat.module.webchat.model.block;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockMessageDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializeMixedBlocks() throws Exception {
        String json = """
                {
                  "blocks": [
                    { "id": 1, "type": "TEXT", "text": "Hello" },
                    { "id": 2, "type": "IMAGE", "fileId": "abc", "mimeType": "image/png" },
                    { "id": 3, "type": "FILE", "fileId": "def", "fileName": "report.pdf", "mimeType": "application/pdf", "size": 1024 }
                  ]
                }
                """;

        BlockMessage message = mapper.readValue(json, BlockMessage.class);

        assertEquals(3, message.blocks().size());

        assertInstanceOf(TextBlock.class, message.blocks().get(0));
        TextBlock text = (TextBlock) message.blocks().get(0);
        assertEquals(1, text.id());
        assertEquals("Hello", text.text());

        assertInstanceOf(ImageBlock.class, message.blocks().get(1));
        ImageBlock image = (ImageBlock) message.blocks().get(1);
        assertEquals(2, image.id());
        assertEquals("abc", image.fileId());
        assertEquals("image/png", image.mimeType());

        assertInstanceOf(FileBlock.class, message.blocks().get(2));
        FileBlock file = (FileBlock) message.blocks().get(2);
        assertEquals(3, file.id());
        assertEquals("def", file.fileId());
        assertEquals("report.pdf", file.fileName());
        assertEquals(1024, file.size());
    }

    @Test
    void deserializeInlineImageBlock() throws Exception {
        String json = """
                {
                  "blocks": [
                    { "id": 1, "type": "IMAGE", "mimeType": "image/png", "inlineData": "iVBOR==" }
                  ]
                }
                """;

        BlockMessage message = mapper.readValue(json, BlockMessage.class);
        ImageBlock image = (ImageBlock) message.blocks().get(0);
        assertNull(image.fileId());
        assertEquals("iVBOR==", image.inlineData());
    }

    @Test
    void serializeRoundTrip() throws Exception {
        BlockMessage original = new BlockMessage(java.util.List.of(
                new TextBlock(1, "test text"),
                new ImageBlock(2, "fid", "image/jpeg", null)
        ));

        String json = mapper.writeValueAsString(original);
        BlockMessage deserialized = mapper.readValue(json, BlockMessage.class);

        assertEquals(2, deserialized.blocks().size());
        assertInstanceOf(TextBlock.class, deserialized.blocks().get(0));
        assertInstanceOf(ImageBlock.class, deserialized.blocks().get(1));
    }
}
