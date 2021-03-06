// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel.server/src/org/activebpel/rt/bpel/server/catalog/resource/AeResourceCache.java,v 1.5 2008/02/28 18:35:56 kpease Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2004 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.server.catalog.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Statistics;

import org.activebpel.rt.AeWSDLException;
import org.activebpel.rt.bpel.AePreferences;
import org.activebpel.rt.bpel.server.catalog.IAeCatalog;
import org.activebpel.rt.bpel.server.catalog.IAeCatalogMapping;
import org.activebpel.rt.bpel.server.engine.AeEngineFactory;
import org.activebpel.rt.bpel.server.wsdl.AeCatalogResourceResolver;
import org.activebpel.rt.bpel.server.wsdl.AeWsdlLocator;
import org.activebpel.rt.wsdl.def.AeBPELExtendedWSDLDef;
import org.activebpel.rt.wsdl.def.AeStandardSchemaResolver;
import org.activebpel.rt.wsdl.def.castor.AeURIResolver;
import org.apache.commons.io.IOUtils;
import org.exolab.castor.xml.schema.Schema;
import org.exolab.castor.xml.schema.reader.SchemaReader;
import org.xml.sax.InputSource;

import bpelg.services.deploy.types.pdd.ReferenceType;

/**
 * In memory cache for resource objects with a max upper limit. Default value,
 * if not in config, is 50.
 */
public class AeResourceCache implements IAeResourceCache, PreferenceChangeListener {
    /**
     * Default max value. Default value is 50.
     */
    public static final int DEFAULT_MAX_VALUE = 50;
    protected final Cache mCache;

    /**
     * Default contructor.
     */
    public AeResourceCache(String aName) {
        int cacheSize = AePreferences.getResourceCacheSize();
        AePreferences.catalog().addPreferenceChangeListener(this);
        CacheManager singletonManager = CacheManager.create();
        mCache = new Cache(aName, cacheSize, false, true, 0, 0);
        singletonManager.addCache(mCache);
    }

    public void updateResource(ReferenceType aKey, Object aObj) {
        removeResource(aKey);
        mCache.put(new Element(toKey(aKey), aObj));
    }

    public Object getResource(ReferenceType aKey) throws AeResourceException {

        if (AeReferenceTypeUtil.isWsdlEntry(aKey))
            return getDefFromCache(aKey);

        if (AeReferenceTypeUtil.isSchemaEntry(aKey))
            return getSchemaDefFromCache(aKey);

        // TODO (cck) is InputSource the best return type for non-wsdl cached
        // locations we need a factory?
        return getInputSourceForLocation(aKey);
    }

    private Object toKey(ReferenceType aKey) {
        return aKey.getTypeURI() + aKey.getNamespace() + aKey.getLocation();
    }

    /**
     * Utility method to locate def. First check is via the LRU cache and if the
     * def is not there, it is reloaded using the wsdl locator and then cached
     * again.
     *
     * @param aKey
     */
    protected AeBPELExtendedWSDLDef getDefFromCache(ReferenceType aKey) throws AeResourceException {
        AeBPELExtendedWSDLDef def = null;
        Element e = mCache.get(toKey(aKey));
        if (e != null)
            def = (AeBPELExtendedWSDLDef) e.getObjectValue();

        if (def == null) {
            def = getDefForLocation(aKey);
            mCache.put(new Element(toKey(aKey), def));
        }

        return def;
    }

    /**
     * Utility method to locate def. First check is via the LRU cache and if the
     * def is not there, it is reloaded using the wsdl locator and then cached
     * again.
     *
     * @param aKey
     */
    protected Schema getSchemaDefFromCache(ReferenceType aKey) throws AeResourceException {
        Schema def = null;
        Element e = mCache.get(toKey(aKey));
        if (e != null)
            def = (Schema) e.getObjectValue();
        if (def == null) {
            def = getSchemaDefForLocation(aKey);
            mCache.put(new Element(toKey(aKey), def));
        }

        return def;
    }

    /**
     * Access the def via the catalog aware locator object.
     *
     * @param aKey
     */
    protected Schema getSchemaDefForLocation(ReferenceType aKey) throws AeResourceException {
        try {
            AeCatalogResourceResolver catalogResolver = new AeCatalogResourceResolver();
            AeWsdlLocator locator = new AeWsdlLocator(catalogResolver, aKey.getLocation());
            SchemaReader reader = new SchemaReader(aKey.getLocation());
            InputSource source = catalogResolver.getInputSource(aKey.getLocation());
            if (source != null)
                reader = new SchemaReader(source);
            else
                reader = new SchemaReader(aKey.getLocation());
            reader.setURIResolver(new AeURIResolver(locator, AeStandardSchemaResolver.newInstance()));
            return reader.read();
        } catch (Exception ex) {
            throw new AeResourceException(ex);
        }
    }

    /**
     * Access the def via the cached wsdl locator object.
     *
     * @param aKey
     */
    protected AeBPELExtendedWSDLDef getDefForLocation(ReferenceType aKey)
            throws AeResourceException {
        AeWsdlLocator locator = new AeWsdlLocator(new AeCatalogResourceResolver(),
                aKey.getLocation());
        String locationHint = locator.getBaseURI();
        try {
            return new AeBPELExtendedWSDLDef(locator, locationHint,
                    AeStandardSchemaResolver.newInstance());
        } catch (AeWSDLException ex) {
            throw new AeResourceException(ex);
        }
    }

    /**
     * Returns an input source for the passed key
     *
     * @param aKey
     */
    protected InputSource getInputSourceForLocation(ReferenceType aKey) throws AeResourceException {
        InputSource source = new InputSource(getStreamForLocation(aKey));
        source.setSystemId(aKey.getLocation());
        return source;
    }

    /**
     * @param aKey
     */
    protected InputStream getStreamForLocation(ReferenceType aKey) throws AeResourceException {
        InputStream stream = null;
        Reader reader = null;
        try {
            byte[] bytes = null;
            Element e = mCache.get(toKey(aKey));
            if (e != null)
                bytes = (byte[]) e.getObjectValue();
            if (bytes == null) {
                InputSource source = getInputSource(aKey.getLocation());
                stream = source.getByteStream();
                if (stream != null) {
                    // read the stream into a buffer and save the buffer in
                    // cache
                    int readLen, totalLen = 0;
                    byte[] buffer = new byte[32768];
                    while ((readLen = stream.read(buffer)) > 0) {
                        totalLen += readLen;
                        if (bytes == null) {
                            byte[] newBuf = new byte[readLen];
                            System.arraycopy(buffer, 0, newBuf, 0, readLen);
                            bytes = newBuf;
                        } else {
                            byte[] newBuf = new byte[totalLen];
                            System.arraycopy(bytes, 0, newBuf, 0, bytes.length);
                            System.arraycopy(buffer, 0, newBuf, bytes.length, readLen);
                            bytes = newBuf;
                        }
                    }

                    // just in case it is an empty file
                    if (bytes.length == 0)
                        bytes = new byte[0];
                } else {
                    reader = source.getCharacterStream();
                    // read the stream into a buffer and save the buffer in
                    // cache
                    int readLen, totalLen = 0;
                    char[] buffer = new char[32768];
                    char[] chars = null;
                    while ((readLen = reader.read(buffer)) > 0) {
                        totalLen += readLen;
                        if (chars == null) {
                            char[] newBuf = new char[readLen];
                            System.arraycopy(buffer, 0, newBuf, 0, readLen);
                            chars = newBuf;
                        } else {
                            char[] newBuf = new char[totalLen];
                            System.arraycopy(chars, 0, newBuf, 0, chars.length);
                            System.arraycopy(buffer, 0, newBuf, chars.length, readLen);
                            chars = newBuf;
                        }
                    }

                    // just in case it is an empty file
                    if (chars.length == 0)
                        chars = new char[0];

                    // convert to byte array for caching
                    String encoding = "utf-8"; //$NON-NLS-1$
                    if (source.getEncoding() != null)
                        encoding = source.getEncoding();
                    bytes = (new String(chars)).getBytes(encoding);
                }

                mCache.put(new Element(toKey(aKey), bytes));
            }
            return new ByteArrayInputStream(bytes);
        } catch (Exception ex) {
            throw new AeResourceException(ex);
        } finally {
            IOUtils.closeQuietly(stream);
            IOUtils.closeQuietly(reader);
        }
    }

    /**
     * Implements method by calling catalog to resolve mapping.
     *
     * @see org.activebpel.rt.bpel.server.wsdl.IAeResourceResolver#getInputSource(java.lang.String)
     */
    public InputSource getInputSource(String aLocationHint) throws Exception {
        InputSource source;

        IAeCatalogMapping mapping = AeEngineFactory.getBean(IAeCatalog.class).getMappingForLocation(
                aLocationHint);
        if (mapping != null) {
            source = mapping.getInputSource();
        } else {
            URL url = new URL(aLocationHint);
            source = new InputSource(url.openStream());
            source.setSystemId(url.toExternalForm());
        }

        return source;

    }

    /**
     * @see org.activebpel.rt.bpel.server.catalog.resource.IAeResourceCache#removeResource(org.activebpel.rt.bpel.server.catalog.resource.IAeResourceKey)
     */
    public boolean removeResource(ReferenceType aKey) {
        // remove the object from the cache (it may not be in memory)
        return mCache.remove(toKey(aKey));
    }

    private void setMaxCacheSize(int aMaxValue) {
        mCache.getCacheConfiguration().setMaxElementsInMemory(aMaxValue);
    }

    /**
     * Clear entries out of the cache.
     */
    public void clear() {
        mCache.removeAll();
    }

    @Override
    public Statistics getStatistics() {
        return mCache.getStatistics();
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent aEvt) {
        setMaxCacheSize(AePreferences.getResourceCacheSize());
    }
}
