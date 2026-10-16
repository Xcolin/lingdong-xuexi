package com.lingdong.learning.student.application.transfer;

import java.util.Locale;
/** Restricts transfer proof to PDF and raster images; stored content is never executable HTML. */
final class TransferMaterialFormat {
 private TransferMaterialFormat(){}
 static String detect(String name,String mime,byte[] bytes){
  if(bytes==null||bytes.length==0||bytes.length>10*1024*1024)throw new IllegalArgumentException("材料不能为空且每份不能超过10MB");
  if(name==null||name.length()>255||name.contains("\r")||name.contains("\n"))throw new IllegalArgumentException("材料名称不合法");
  String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
  String detected=null;
  if(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10&&extension.equals("png"))detected="image/png";
  if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255&&(extension.equals("jpg")||extension.equals("jpeg")))detected="image/jpeg";
  if(bytes.length>=5&&bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F'&&bytes[4]=='-'&&extension.equals("pdf"))detected="application/pdf";
  String normalized=mime==null?"":mime.trim().toLowerCase(Locale.ROOT);
  if(detected==null||(!normalized.isEmpty()&&!normalized.equals("application/octet-stream")&&!normalized.equals(detected)))throw new IllegalArgumentException("材料扩展名、内容类型与文件格式不一致");
  return detected;
 }
}
