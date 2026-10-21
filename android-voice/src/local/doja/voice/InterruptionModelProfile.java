package local.doja.voice;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** Same immutable Spanish weights/search thresholds, smaller neural work units. */
final class InterruptionModelProfile {
    static final int FRAMES_PER_CHUNK=6; // 60 ms input, divisible by the existing subsampling factor 3.
    static final String SOURCE_CONF_SHA256="330c1b50575d0c1c0402276c9cdc7a589ebd04de5d3de17695dd7e6db701da54";
    static String configuration(byte[] original) throws Exception {
        if(original.length<1 || original.length>8192) throw new IOException("Invalid public model configuration size");
        StringBuilder sha=new StringBuilder();
        for(byte value:MessageDigest.getInstance("SHA-256").digest(original)) sha.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        if(!SOURCE_CONF_SHA256.equals(sha.toString())) throw new IOException("Unexpected public model configuration");
        return new String(original,StandardCharsets.UTF_8)+"\n--frames-per-chunk="+FRAMES_PER_CHUNK+"\n";
    }
    static File directory(File source) throws Exception {
        Path original=source.toPath();
        if(Files.isSymbolicLink(original) || !source.isDirectory()) throw new IOException("Invalid model directory");
        Path runtime=original.resolveSibling("vosk-es-stream6-v1");
        createDirectory(runtime); createDirectory(runtime.resolve("conf"));
        // Do not rewrite or copy the deployed weights/config. Every link is fixed
        // and checked; no media/user paths are accepted by the model loader.
        for(String name:new String[]{"am","graph","ivector"}) link(runtime.resolve(name),original.resolve(name));
        link(runtime.resolve("conf/mfcc.conf"),original.resolve("conf/mfcc.conf"));
        Path conf=runtime.resolve("conf/model.conf");
        Path sourceConf=original.resolve("conf/model.conf");
        if(Files.isSymbolicLink(sourceConf) || Files.size(sourceConf)>8192) throw new IOException("Invalid source model configuration");
        byte[] expected=configuration(Files.readAllBytes(sourceConf)).getBytes(StandardCharsets.UTF_8);
        if(!Files.exists(conf,LinkOption.NOFOLLOW_LINKS)) Files.write(conf,expected,StandardOpenOption.CREATE_NEW);
        if(Files.isSymbolicLink(conf) || Files.size(conf)!=expected.length || !Arrays.equals(expected,Files.readAllBytes(conf)))
            throw new IOException("Unexpected runtime model configuration");
        return runtime.toFile();
    }
    private static void createDirectory(Path path) throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path);
        if(Files.isSymbolicLink(path) || !Files.isDirectory(path)) throw new IOException("Invalid runtime model directory");
    }
    private static void link(Path path,Path target) throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) Files.createSymbolicLink(path,target);
        if(!Files.isSymbolicLink(path) || !Files.readSymbolicLink(path).equals(target)) throw new IOException("Invalid fixed model link");
    }
}
