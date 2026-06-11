package com.laboa.search.service;

public interface SearchService {

    SearchResult search(String keyword, String type, int page, int size);
}