package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransferMaterialFormatTest {
 @Test void acceptsOnlyMatchingMaterialSignaturesNamesAndMimeTypes(){
  assertEquals("application/pdf",TransferMaterialFormat.detect("proof.pdf","application/pdf","%PDF-1.7\nfixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
  assertEquals("image/jpeg",TransferMaterialFormat.detect("proof.JPG","application/octet-stream",new byte[]{(byte)255,(byte)216,(byte)255,1}));
  byte[] png={(byte)137,80,78,71,13,10,26,10};
  assertEquals("image/png",TransferMaterialFormat.detect("proof.png",null,png));
  assertThrows(IllegalArgumentException.class,()->TransferMaterialFormat.detect("proof.pdf","application/pdf",png));
  assertThrows(IllegalArgumentException.class,()->TransferMaterialFormat.detect("proof.png","text/html",png));
  assertThrows(IllegalArgumentException.class,()->TransferMaterialFormat.detect("proof.html","text/html","<html>fixture".getBytes()));
  assertThrows(IllegalArgumentException.class,()->TransferMaterialFormat.detect("proof.pdf",null,new byte[0]));
  assertThrows(IllegalArgumentException.class,()->TransferMaterialFormat.detect("proof.pdf",null,new byte[10*1024*1024+1]));
 }
}
