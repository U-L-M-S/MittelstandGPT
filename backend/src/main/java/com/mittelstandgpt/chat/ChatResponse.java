package com.mittelstandgpt.chat;

import java.util.List;

/** The assistant's answer together with the sources it was grounded on. */
public record ChatResponse(String answer, List<Source> sources) {}
