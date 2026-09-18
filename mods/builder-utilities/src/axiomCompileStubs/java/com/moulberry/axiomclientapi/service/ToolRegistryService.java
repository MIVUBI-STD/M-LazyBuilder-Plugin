package com.moulberry.axiomclientapi.service;

import com.moulberry.axiomclientapi.CustomTool;

/** Compile-only subset of the MIT-licensed AxiomClientAPI ToolRegistryService contract. */
public interface ToolRegistryService {
    void register(CustomTool customTool);
}
