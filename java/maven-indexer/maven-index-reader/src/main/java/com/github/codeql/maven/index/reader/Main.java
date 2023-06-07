package com.github.codeql.maven.index.reader;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.MergeInfo;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.util.Bits;

public class Main {

  public static void main(String[] args) {

    if (args.length != 1) {
      System.err.println("Usage: java com.github.codeql.maven.index.reader.Main indexDir");
      System.exit(1);
    }
    try {
      run(args[0]);
    } catch (IOException e) {
      System.err.println(e.toString());
      System.exit(1);
    } 

  }
  
  private static Set<String> fieldsUAndI = Set.of("u", "i");

  private static boolean isJarRecord(String ufield, String ifield) {
    return ufield.endsWith("|NA") || ufield.endsWith("tests|jar");
    // Checking for the file extension declared by the 'i' field seems to be simply unreliable --
    // the index only contains one record no matter how many files were uploaded (jars, poms, signatures)...
    // and seems to choose among those extensions arbitrarily
  }
  
  public static void run(String inputDirStr) throws IOException {

    FSDirectory databaseDir = FSDirectory.open(new File(inputDirStr));
    
    SegmentInfos sis = new SegmentInfos();
    sis.read(databaseDir);
    
    for (SegmentCommitInfo info : sis) {
      IOContext context = new IOContext(new MergeInfo(info.info.getDocCount(), info.sizeInBytes(), true, -1));
      SegmentReader reader = new SegmentReader(info, 1, context);
      int limit = info.info.getDocCount();
      Bits liveDocs = reader.getLiveDocs();
      for (int i = 0; i < limit; ++i) {
        if (liveDocs != null && !liveDocs.get(i))
          continue;
        Document doc = reader.document(i, fieldsUAndI);
        String ufield = doc.get("u");
        String ifield = doc.get("i");
        if (ufield != null && ifield != null && isJarRecord(ufield, ifield))
          System.out.printf("%s %s\n", ufield, ifield);
      }
      reader.close();
    }

  }

}
