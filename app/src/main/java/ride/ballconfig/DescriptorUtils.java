package ride.ballconfig;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import java.util.Arrays;
import java.util.zip.Inflater;

public class DescriptorUtils {
    public static Descriptors.Descriptor parseConfigDescriptor(byte[] compressedDescriptor) throws Exception {
        byte[] tmpBuffer = new byte[1024*10];
        Inflater decompresser = new Inflater();
        decompresser.setInput(compressedDescriptor, 0, compressedDescriptor.length);

        int resultLength = decompresser.inflate(tmpBuffer);
        decompresser.end();

        byte[] decompressedDescriptor = Arrays.copyOf(tmpBuffer, resultLength);
        DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(decompressedDescriptor);
        if (descriptorSet.getFileCount() != 1) throw new Exception("Bad descriptor set, more than one file in input");
        DescriptorProtos.FileDescriptorProto file = descriptorSet.getFile(0);
        Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        return fileDescriptor.findMessageTypeByName("Config");
    }

    public static void setFieldsToTheirDefaultValues(DynamicMessage.Builder bldr) {
        for (Descriptors.FieldDescriptor field: bldr.getDescriptorForType().getFields()) {
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                DynamicMessage.Builder child_bldr = ((DynamicMessage)bldr.getField(field)).toBuilder();
                setFieldsToTheirDefaultValues(child_bldr);
                bldr.setField(field, child_bldr.build());
            }
            else {
                bldr.setField(field, bldr.getField(field));
            }
        }
    }
}
