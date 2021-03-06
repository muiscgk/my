/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2011 OpenConcerto, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU General Public License Version 3
 * only ("GPL"). You may not use this file except in compliance with the License. You can obtain a
 * copy of the License at http://www.gnu.org/licenses/gpl-3.0.html See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 */
 
 package org.openconcerto.sql;

import org.openconcerto.sql.element.SQLElement;
import org.openconcerto.sql.element.SQLElementDirectory;
import org.openconcerto.sql.element.SQLElementDirectory.DirectoryListener;
import org.openconcerto.sql.model.DBRoot;
import org.openconcerto.sql.model.DBStructureItem;
import org.openconcerto.sql.model.DBSystemRoot;
import org.openconcerto.sql.model.HierarchyLevel;
import org.openconcerto.sql.model.SQLBase;
import org.openconcerto.sql.model.SQLDataSource;
import org.openconcerto.sql.model.SQLFilter;
import org.openconcerto.sql.model.SQLRow;
import org.openconcerto.sql.model.SQLServer;
import org.openconcerto.sql.model.SQLSystem;
import org.openconcerto.sql.request.SQLFieldTranslator;
import org.openconcerto.utils.CollectionMap;
import org.openconcerto.utils.ExceptionHandler;
import org.openconcerto.utils.FileUtils;
import org.openconcerto.utils.LogUtils;
import org.openconcerto.utils.StreamUtils;
import org.openconcerto.utils.cc.IClosure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.collections.Predicate;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * A configuration which takes its values primarily from Properties. You should also subclass its
 * different protected get*() methods. Used properties :
 * <dl>
 * <dt>server.ip</dt>
 * <dd>ip address of the SQL server</dd>
 * <dt>server.driver</dt>
 * <dd>the RDBMS, see {@link org.openconcerto.sql.model.SQLDataSource#DRIVERS}</dd>
 * <dt>server.login</dt>
 * <dd>the login</dd>
 * <dt>server.password</dt>
 * <dd>the password</dd>
 * <dt>server.base</dt>
 * <dd>the database (only used for systems where the root level is not SQLBase)</dd>
 * <dt>base.root</dt>
 * <dd>the name of the DBRoot</dd>
 * <dt>customer</dt>
 * <dd>used to find the default base and the mapping</dd>
 * <dt>JDBC_CONNECTION*</dt>
 * <dd>see {@link #JDBC_CONNECTION}</dd>
 * </dl>
 * 
 * @author Sylvain CUAZ
 * @see #getShowAs()
 */
public class PropsConfiguration extends Configuration {

    /**
     * Properties prefixed with this string will be passed to the datasource as connection
     * properties.
     */
    public static final String JDBC_CONNECTION = "jdbc.connection.";
    public static final String LOG = "log.level.";
    /**
     * If this system property is set to <code>true</code> then {@link #setupLogging(String)} will
     * redirect {@link System#err} and {@link System#out}.
     */
    public static final String REDIRECT_TO_FILE = "redirectToFile";

    protected static enum FileMode {
        IN_JAR, NORMAL_FILE
    };

    // eg 2009-03/26_thursday : ordered and grouped by month
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM/dd_EEEE");

    public static String getHostname() {
        final InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "local";
        }
        return addr.getHostName();
    }

    protected static Properties create(InputStream f, Properties defaults) throws IOException {
        Properties props = new Properties(defaults);
        if (f != null) {
            props.load(f);
            f.close();
        }
        return props;
    }

    public static final Properties DEFAULTS;
    static {
        DEFAULTS = new Properties();
        final File wd = new File(System.getProperty("user.dir"));
        DEFAULTS.setProperty("wd", wd.getPath());
        DEFAULTS.setProperty("customer", "test");
        DEFAULTS.setProperty("server.ip", "127.0.0.1");
        DEFAULTS.setProperty("server.login", "root");
    }

    private final Properties props;

    // sql tree
    private SQLServer server;
    private DBSystemRoot sysRoot;
    private DBRoot root;
    // rest
    private ShowAs showAs;
    private SQLFilter filter;
    private SQLFieldTranslator translator;
    private SQLElementDirectory directory;
    private DirectoryListener directoryListener;
    private File wd;
    // split sql tree and the rest since creating the tree is costly
    // and nodes are inter-dependant, while the rest is mostly fast
    // different instances, otherwise lock every Conf instances
    private final Object treeLock = new String("treeLock");
    private final Object restLock = new String("everythingElseLock");

    // * toAdd
    private final List<Configuration> showAsToAdd;
    private final List<Configuration> directoryToAdd;
    private final List<Configuration> translationsToAdd;

    // SSL
    private Session conn;
    private boolean isUsingSSH;

    public PropsConfiguration() throws IOException {
        this(new File("fwk_SQL.properties"), DEFAULTS);
    }

    /**
     * Creates a new setup.
     * 
     * @param f the file from which to load.
     * @param defaults the defaults, can be <code>null</code>.
     * @throws IOException if an error occurs while reading f.
     */
    public PropsConfiguration(File f, Properties defaults) throws IOException {
        this(new FileInputStream(f), defaults);
    }

    public PropsConfiguration(InputStream f, Properties defaults) throws IOException {
        this(create(f, defaults));
    }

    public PropsConfiguration(Properties props) {
        this.props = props;
        this.showAsToAdd = new ArrayList<Configuration>();
        this.directoryToAdd = new ArrayList<Configuration>();
        this.translationsToAdd = new ArrayList<Configuration>();
        this.setUp();
    }

    @Override
    public void destroy() {
        closeSSLConnection();
        if (this.server != null) {
            this.server.destroy();
        }
        if (this.directoryListener != null)
            this.directory.removeListener(this.directoryListener);
    }

    public final String getProperty(String name) {
        return this.props.getProperty(name);
    }

    public final String getProperty(String name, String def) {
        return this.props.getProperty(name, def);
    }

    // since null aren't allowed, null means remove
    protected final void setProperty(String name, String val) {
        if (val == null)
            this.props.remove(name);
        else
            this.props.setProperty(name, val);
    }

    private void setUp() {
        this.sysRoot = null;
        this.setTranslator(null);
        this.setDirectory(null);
        this.setShowAs(null);
        this.setFilter(null);
    }

    protected final String getSystem() {
        return this.getProperty("server.driver");
    }

    protected String getLogin() {
        return this.getProperty("server.login");
    }

    protected String getPassword() {
        return this.getProperty("server.password");
    }

    public String getDefaultBase() {
        final boolean rootIsBase = SQLSystem.get(this.getSystem()).getDBRootLevel().equals(HierarchyLevel.SQLBASE);
        return rootIsBase ? this.getRootName() : this.getSystemRootName();
    }

    /**
     * Return the correct stream depending on file mode. If file mode is
     * {@link FileMode#NORMAL_FILE} it will first check if a file named <code>name</code> exists,
     * otherwise it will look in the jar.
     * 
     * @param name name of the stream, eg /ilm/f.xml.
     * @return the corresponding stream, or <code>null</code> if not found.
     */
    public final InputStream getStream(String name) {
        final File f = getFile(name);
        if (mustUseClassloader(f)) {
            return this.getClass().getResourceAsStream(name);
        } else
            try {
                return new FileInputStream(f);
            } catch (FileNotFoundException e) {
                return null;
            }
    }

    private File getFile(String name) {
        return new File(name.startsWith("/") ? name.substring(1) : name);
    }

    private boolean mustUseClassloader(final File f) {
        return this.getFileMode() == FileMode.IN_JAR || !f.exists();
    }

    public final String getResource(String name) {
        final File f = getFile(name);
        if (mustUseClassloader(f)) {
            return this.getClass().getResource(name).toExternalForm();
        } else {
            return f.getAbsolutePath();
        }
    }

    protected FileMode getFileMode() {
        return FileMode.IN_JAR;
    }

    protected final DBRoot createRoot() {
        if (getRootName() != null)
            return this.getSystemRoot().getRoot(getRootName());
        else
            throw new NullPointerException("no rootname");
    }

    public String getRootName() {
        return this.getProperty("base.root");
    }

    protected SQLFilter createFilter() {
        return SQLFilter.create(this.getSystemRoot(), getDirectory());
    }

    public String getWanHostAndPort() {
        final String wanAddr = getProperty("server.wan.addr");
        final String wanPort = getProperty("server.wan.port", "22");
        return wanAddr + ":" + wanPort;
    }

    public boolean isUsingSSH() {
        return this.isUsingSSH;
    }

    protected SQLServer createServer() {
        final String wanAddr = getProperty("server.wan.addr");
        final String wanPort = getProperty("server.wan.port");
        if (wanAddr == null || wanPort == null)
            return doCreateServer();

        final Logger log = Log.get();
        Exception origExn = null;
        final SQLServer defaultServer;
        if (!"true".equals(getProperty("server.wan.only"))) {
            try {
                defaultServer = doCreateServer();
                // works since all ds params are provided by doCreateServer()
                defaultServer.getSystemRoot(getSystemRootName());
                // ok
                log.config("using " + defaultServer);

                return defaultServer;
            } catch (RuntimeException e) {
                origExn = e;
                // on essaye par SSL
                log.config(e.getLocalizedMessage());
            }
            assert origExn != null;
        }
        this.openSSLConnection(wanAddr, Integer.valueOf(wanPort));
        this.isUsingSSH = true;
        log.info("ssl connection to " + this.conn.getHost() + ":" + this.conn.getPort());
        final int localPort = 5436;
        try {
            // TODO add and use server.port
            final String[] serverAndPort = getProperty("server.ip").split(":");
            log.info("ssl tunnel from local port " + localPort + " to remote " + serverAndPort[0] + ":" + serverAndPort[1]);
            this.conn.setPortForwardingL(localPort, serverAndPort[0], Integer.valueOf(serverAndPort[1]));
        } catch (Exception e1) {
            throw new IllegalStateException("Impossible de créer la liaison sécurisée. Vérifier que le logiciel n'est pas déjà lancé.", e1);
        }
        setProperty("server.ip", "localhost:" + localPort);
        final SQLServer serverThruSSL = doCreateServer();
        try {
            serverThruSSL.getSystemRoot(getSystemRootName());
        } catch (Exception e) {
            this.closeSSLConnection();
            throw new IllegalStateException("Couldn't connect through SSL : " + e.getLocalizedMessage(), origExn);
        }
        return serverThruSSL;

    }

    private SQLServer doCreateServer() {
        // give login/password as its often the case that they are the same for all the bases of a
        // server (mandated for MySQL : when the graph is built, it needs access to all the bases)
        return new SQLServer(getSystem(), this.getProperty("server.ip"), null, getLogin(), getPassword(), new IClosure<DBSystemRoot>() {
            @Override
            public void executeChecked(DBSystemRoot input) {
                input.getRootsToMap().addAll(getRootsToMap());
            }
        }, new IClosure<SQLDataSource>() {
            @Override
            public void executeChecked(SQLDataSource input) {
                initDS(input);
            }
        });
    }

    private void openSSLConnection(String addr, int port) {
        final String username = getSSLUserName();
        boolean isAuthenticated = false;

        final JSch jsch = new JSch();
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream(700);
            final String name = username + "_dsa";
            final InputStream in = getClass().getResourceAsStream(name);
            if (in == null)
                throw new IllegalStateException("Missing private key " + getClass().getCanonicalName() + "/" + name);
            StreamUtils.copy(in, out);
            in.close();
            jsch.addIdentity(username, out.toByteArray(), null, null);

            this.conn = jsch.getSession(username, addr, port);
            final Properties config = new Properties();
            // Set StrictHostKeyChecking property to no to avoid UnknownHostKey issue
            config.put("StrictHostKeyChecking", "no");
            // *2 gain
            config.put("compression.s2c", "zlib@openssh.com,zlib,none");
            config.put("compression.c2s", "zlib@openssh.com,zlib,none");
            this.conn.setConfig(config);
            // wait no more than 6 seconds for TCP connection
            this.conn.connect(6000);

            isAuthenticated = true;
        } catch (Exception e) {
            throw new IllegalStateException("Connection failed", e);
        }
        if (!isAuthenticated)
            throw new IllegalStateException("Authentication failed.");
    }

    public String getSSLUserName() {
        return this.getProperty("server.wan.user");
    }

    private void closeSSLConnection() {
        if (this.conn != null) {
            this.conn.disconnect();
            this.conn = null;
        }
    }

    protected Collection<String> getRootsToMap() {
        final Set<String> res = new HashSet<String>();

        if (this.getRootName() != null)
            res.add(this.getRootName());
        final String rootsToMap = getProperty("systemRoot.rootsToMap");
        if (rootsToMap != null)
            res.addAll(SQLRow.toList(rootsToMap));

        return res;
    }

    public String getSystemRootName() {
        return this.getProperty("systemRoot");
    }

    protected DBSystemRoot createSystemRoot() {
        // all ds params specified by createServer()
        final DBSystemRoot res = this.getServer(false).getSystemRoot(this.getSystemRootName());
        // handle case when the root is not yet created
        if (res.getChildrenNames().contains(this.getRootName()))
            res.setDefaultRoot(this.getRootName());
        final String rootsPath = getProperty("systemRoot.rootPath");
        if (rootsPath != null) {
            for (final String root : SQLRow.toList(rootsPath))
                // not all the items of the path may exist in every databases (eg Controle.Common)
                if (res.getChildrenNames().contains(root))
                    res.appendToRootPath(root);
        }
        return res;
    }

    protected void initDS(final SQLDataSource ds) {
        ds.setCacheEnabled(true);
        propIterate(new IClosure<String>() {
            public void executeChecked(String propName) {
                final String jdbcName = propName.substring(JDBC_CONNECTION.length());
                ds.addConnectionProperty(jdbcName, PropsConfiguration.this.getProperty(propName));
            }
        }, JDBC_CONNECTION);
    }

    public final void propIterate(final IClosure<String> cl, final String startsWith) {
        this.propIterate(cl, new Predicate() {
            public boolean evaluate(Object propName) {
                return ((String) propName).startsWith(startsWith);
            }
        });
    }

    /**
     * Apply <code>cl</code> for each property that matches <code>filter</code>.
     * 
     * @param cl what to do for each found property.
     * @param filter which property to use.
     */
    public final void propIterate(final IClosure<String> cl, final Predicate filter) {
        final Enumeration iter = this.props.propertyNames();
        while (iter.hasMoreElements()) {
            final String propName = (String) iter.nextElement();
            if (filter.evaluate(propName)) {
                cl.executeChecked(propName);
            }
        }
    }

    /**
     * For each property starting with {@link #LOG}, set the level of the specified logger to the
     * property's value. Eg if there's "log.level.=FINE", the root logger will be set to log FINE
     * messages.
     */
    public final void setLoggersLevel() {
        this.propIterate(new IClosure<String>() {
            public void executeChecked(String propName) {
                final String logName = propName.substring(LOG.length());
                LogUtils.getLogger(logName).setLevel(Level.parse(getProperty(propName)));
            }
        }, LOG);
    }

    public void setupLogging() {
        this.setupLogging("logs");
    }

    public void setupLogging(final String dirName) {
        this.setupLogging(dirName, Boolean.getBoolean(REDIRECT_TO_FILE));
    }

    public void setupLogging(final String dirName, final boolean redirectToFile) {
        final File logDir;
        try {
            final File softLogDir = new File(this.getWD() + "/" + dirName + "/" + getHostname() + "-" + System.getProperty("user.name"));
            softLogDir.mkdirs();
            if (softLogDir.canWrite())
                logDir = softLogDir;
            else {
                final File homeLogDir = new File(System.getProperty("user.home") + "/." + this.getAppName() + "/" + dirName);
                FileUtils.mkdir_p(homeLogDir);
                logDir = homeLogDir;
            }
            System.out.println("Log directory: " + logDir.getAbsolutePath());
            if (!logDir.exists()) {
                System.err.println("Log directory: " + logDir.getAbsolutePath() + " DOEST NOT EXISTS!!!");
            }
            if (!logDir.canWrite()) {
                System.err.println("Log directory: " + logDir.getAbsolutePath() + " is NOT WRITABLE");
            }
        } catch (IOException e) {
            throw new IllegalStateException("unable to create log dir", e);
        }
        final String logNameBase = this.getAppName() + "_" + DATE_FORMAT.format(new Date());

        // must be done first, otherwise log output not redirected
        if (redirectToFile) {
            final File logFile = new File(logDir, (logNameBase + ".txt"));
            logFile.getParentFile().mkdirs();
            try {
                System.out.println("Log file: " + logFile.getAbsolutePath());
                final PrintStream ps = new PrintStream(new FileOutputStream(logFile, true));
                System.setErr(ps);
                System.setOut(ps);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("unable to write to log file", e);
            }
            // Takes about 350ms so run it async
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileUtils.ln(logFile, new File(logDir, "last.log"));
                    } catch (IOException e) {
                        // the link is not important
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            System.out.println("Log not redirected to file");
        }

        // removes default
        LogUtils.rmRootHandlers();
        // add console logger
        LogUtils.setUpConsoleHandler();
        // add file logger
        try {
            final File logFile = new File(logDir, this.getAppName() + "-%u-age%g.log");
            logFile.getParentFile().mkdirs();
            System.out.println("Logger logs: " + logFile.getAbsolutePath());
            // 2 files of at most 5M, each new launch append
            // if multiple concurrent launches %u is used
            final FileHandler fh = new FileHandler(logFile.getPath(), 5 * 1024 * 1024, 2, true);
            fh.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(fh);
        } catch (IOException e) {
            ExceptionHandler.handle("Enregistrement du Logger désactivé", e);
        }

        this.setLoggersLevel();
    }

    public void tearDownLogging() {
        this.tearDownLogging(Boolean.getBoolean(REDIRECT_TO_FILE));
    }

    public void tearDownLogging(final boolean redirectToFile) {
        LogUtils.rmRootHandlers();
        if (redirectToFile) {
            System.out.close();
            System.err.close();
        }
    }

    protected ShowAs createShowAs() {
        final ShowAs res = new ShowAs(this.getRoot());
        assert this.directoryListener == null;
        this.directoryListener = new DirectoryListener() {
            @Override
            public void elementRemoved(SQLElement elem) {
                res.removeTable(elem.getTable());
            }

            @Override
            public void elementAdded(SQLElement elem) {
                final CollectionMap<String, String> sa = elem.getShowAs();
                if (sa != null) {
                    for (final Entry<String, Collection<String>> e : sa.entrySet()) {
                        if (e.getKey() == null) {
                            res.show(elem.getTable(), (List<String>) e.getValue());
                        } else
                            res.show(elem.getTable().getField(e.getKey()), (List<String>) e.getValue());
                    }
                }
            }
        };
        synchronized (this.getDirectory()) {
            for (final SQLElement elem : this.getDirectory().getElements()) {
                this.directoryListener.elementAdded(elem);
            }
            this.getDirectory().addListener(this.directoryListener);
        }
        return res;
    }

    protected SQLElementDirectory createDirectory() {
        return new SQLElementDirectory();
    }

    // items will be passed to #getStream(String)
    protected List<String> getMappings() {
        return Arrays.asList("mapping.xml", "mapping-" + this.getProperty("customer") + ".xml");
    }

    protected SQLFieldTranslator createTranslator() {
        final List<String> mappings = getMappings();
        if (mappings.size() == 0)
            throw new IllegalStateException("empty mappings");

        final SQLFieldTranslator trns = new SQLFieldTranslator(this.getRoot(), PropsConfiguration.class.getResourceAsStream("mapping.xml"), this.getDirectory());
        for (final String m : mappings) {
            // do not force to have one mapping for each client
            final InputStream in = this.getStream(m);
            if (in != null)
                trns.load(this.getRoot(), in);
        }
        return trns;
    }

    protected File createWD() {
        return new File(this.getProperty("wd"));
    }

    // *** add

    /**
     * Add the passed Configuration to this. If an item is not already created, this method won't,
     * instead the item to add will be stored. Also items of this won't be replaced by those of
     * <code>conf</code>.
     * 
     * @param conf the conf to add.
     */
    public final Configuration add(Configuration conf) {
        if (this.showAs != null)
            this.getShowAs().putAll(conf.getShowAs());
        else
            this.showAsToAdd.add(conf);

        if (this.translator != null)
            this.getTranslator().putAll(conf.getTranslator());
        else
            this.translationsToAdd.add(conf);

        if (this.directory != null)
            this.getDirectory().putAll(conf.getDirectory());
        else
            this.directoryToAdd.add(conf);

        return this;
    }

    // *** getters

    public final ShowAs getShowAs() {
        synchronized (this.restLock) {
            if (this.showAs == null) {
                this.setShowAs(this.createShowAs());
                for (final Configuration s : this.showAsToAdd)
                    this.getShowAs().putAll(s.getShowAs());
                this.showAsToAdd.clear();
            }
        }
        return this.showAs;
    }

    public final SQLBase getBase() {
        return this.getNode(SQLBase.class);
    }

    public final DBRoot getRoot() {
        synchronized (this.treeLock) {
            if (this.root == null)
                this.setRoot(this.createRoot());
        }
        return this.root;
    }

    @Override
    public final DBSystemRoot getSystemRoot() {
        synchronized (this.treeLock) {
            if (this.sysRoot == null)
                this.sysRoot = this.createSystemRoot();
        }
        return this.sysRoot;
    }

    /**
     * Get the node of the asked class, creating just the necessary instances (ie getNode(Server)
     * won't do a getBase().getServer()).
     * 
     * @param <T> the type wanted.
     * @param clazz the class wanted, eg SQLBase.class, DBSystemRoot.class.
     * @return the corresponding instance, eg getBase() for SQLBase, getServer() or getBase() for
     *         DBSystemRoot depending on the SQL system.
     */
    public final <T extends DBStructureItem> T getNode(Class<T> clazz) {
        final SQLSystem sys = this.getServer().getSQLSystem();
        final HierarchyLevel l = sys.getLevel(clazz);
        if (l == HierarchyLevel.SQLSERVER)
            return this.getServer().getAnc(clazz);
        else if (l == sys.getLevel(DBSystemRoot.class))
            return this.getSystemRoot().getAnc(clazz);
        else if (l == sys.getLevel(DBRoot.class))
            return this.getRoot().getAnc(clazz);
        else
            throw new IllegalArgumentException("doesn't know an item of " + clazz);
    }

    public final SQLServer getServer() {
        return this.getServer(true);
    }

    private final SQLServer getServer(final boolean initSysRoot) {
        synchronized (this.treeLock) {
            if (this.server == null) {
                this.setServer(this.createServer());
                // necessary otherwise the returned server has no datasource
                // (eg getChildren() will fail)
                if (initSysRoot && this.server.getSQLSystem().getLevel(DBSystemRoot.class) == HierarchyLevel.SQLSERVER)
                    this.getSystemRoot();
            }
        }
        return this.server;
    }

    public final SQLFilter getFilter() {
        synchronized (this.restLock) {
            if (this.filter == null)
                this.setFilter(this.createFilter());
        }
        return this.filter;
    }

    public final SQLFieldTranslator getTranslator() {
        synchronized (this.restLock) {
            if (this.translator == null) {
                this.setTranslator(this.createTranslator());
                for (final Configuration s : this.translationsToAdd)
                    this.getTranslator().putAll(s.getTranslator());
                this.translationsToAdd.clear();
            }
        }
        return this.translator;
    }

    public final SQLElementDirectory getDirectory() {
        synchronized (this.restLock) {
            if (this.directory == null) {
                this.setDirectory(this.createDirectory());
                for (final Configuration s : this.directoryToAdd)
                    this.getDirectory().putAll(s.getDirectory());
                this.directoryToAdd.clear();
            }
        }
        return this.directory;
    }

    @Override
    public String getAppName() {
        return this.getProperty("app.name");
    }

    @Override
    public final File getWD() {
        synchronized (this.restLock) {
            if (this.wd == null)
                this.setWD(this.createWD());
        }
        return this.wd;
    }

    // *** setters

    // MAYBE add synchronized (not necessary since they're private, and only called with the lock)

    private final void setFilter(SQLFilter filter) {
        this.filter = filter;
    }

    private void setServer(SQLServer server) {
        this.server = server;
    }

    private final void setRoot(DBRoot root) {
        this.root = root;
    }

    private final void setShowAs(ShowAs showAs) {
        this.showAs = showAs;
    }

    private final void setTranslator(SQLFieldTranslator translator) {
        this.translator = translator;
    }

    private final void setDirectory(SQLElementDirectory directory) {
        this.directory = directory;
    }

    private final void setWD(File dir) {
        this.wd = dir;
    }

}
