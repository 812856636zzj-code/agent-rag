package com.example.rag.service;

import java.util.List;

public interface SentenceSplitter {

    List<String> split(String text);
}
