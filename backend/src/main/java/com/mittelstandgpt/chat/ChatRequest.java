package com.mittelstandgpt.chat;

/** A user question sent to the RAG endpoint. */
public record ChatRequest(String question) {}
