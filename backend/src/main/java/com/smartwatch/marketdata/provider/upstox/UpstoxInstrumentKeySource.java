package com.smartwatch.marketdata.provider.upstox;

import java.util.Collection;
import java.util.List;

/**
 * Reads stored Upstox instrument keys. It does not call Upstox.
 */
public interface UpstoxInstrumentKeySource {

    List<UpstoxInstrumentMapping> findForSymbols(Collection<String> symbols);

    List<UpstoxInstrumentMapping> findAll();
}
