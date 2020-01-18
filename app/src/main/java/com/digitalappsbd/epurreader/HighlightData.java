package com.digitalappsbd.epurreader;

import com.folioreader.model.HighLight;
import java.util.Date;

public class HighlightData implements HighLight {

  private String bookId;
  private String content;
  private Date date;
  private String type;
  private int pageNumber;
  private String pageId;
  private String rangy;
  private String uuid;
  private String note;

  @Override
  public String toString() {
    return "HighlightData{" +
        "bookId='" + bookId + '\'' +
        ", content='" + content + '\'' +
        ", date=" + date +
        ", type='" + type + '\'' +
        ", pageNumber=" + pageNumber +
        ", pageId='" + pageId + '\'' +
        ", rangy='" + rangy + '\'' +
        ", uuid='" + uuid + '\'' +
        ", note='" + note + '\'' +
        '}';
  }

  @Override
  public String getBookId() {
    return bookId;
  }

  @Override
  public String getContent() {
    return content;
  }

  @Override
  public Date getDate() {
    return date;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public int getPageNumber() {
    return pageNumber;
  }

  @Override
  public String getPageId() {
    return pageId;
  }

  @Override
  public String getRangy() {
    return rangy;
  }

  @Override
  public String getUUID() {
    return uuid;
  }

  @Override
  public String getNote() {
    return note;
  }
}