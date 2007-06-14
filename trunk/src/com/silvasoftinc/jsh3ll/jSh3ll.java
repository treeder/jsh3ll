// This software code is made available "AS IS" without warranties of any
// kind.  You may copy, display, modify and redistribute the software
// code either by itself or as incorporated into your code; provided that
// you do not remove any proprietary notices.  Your use of this software
// code is at your own risk and you waive any claim against Amazon
// Digital Services, Inc. or its affiliates with respect to your use of
// this software code. (c) 2006 Amazon Digital Services, Inc. or its
// affiliates.

// Copyright (c) 2006 SilvaSoft, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the 
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.

// author:    http://www.silvasoftinc.com
// author:    Dominic Da Silva (dominic.dasilva@gmail.com)
// version:   3.4
// date:      05/22/2006

package com.silvasoftinc.jsh3ll;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import com.amazon.s3.AWSAuthConnection;
import com.amazon.s3.Bucket;
import com.amazon.s3.GetStreamResponse;
import com.amazon.s3.ListEntry;
import com.amazon.s3.Response;
import com.amazon.s3.S3Object;
import com.silvasoftinc.s3.S3AtomHelper;
import com.silvasoftinc.s3.S3Helper;
import com.silvasoftinc.s3.S3RSSHelper;
import com.silvasoftinc.s3.S3StreamObject;

/**
 * -----------------------------------------------------------------------------
 * jSh3ll is a modified version of s3shell.java provided with the Amazon
 * S3Shell. See jSh3ll_README.txt for details.
 * 
 * The following enhancements have been made: 1. Leverages the Amazon REST
 * library for Java. 2. Support for ACL retrieval for buckets and items. 3.
 * Support for HEAD retrieval for buckets and items. 4. Support for getting a
 * BitTorrent file (.torrent) for an item. 5. Support for ACL updating of
 * buckets and items. 6. Support for creating a bucket with a specified ACL. 7.
 * Support for putting an item with a specified ACL into a bucket. 8. Error
 * handling. 9. File compression support (using ZLIB). 10. Script mode support.
 * 11. Streaming file upload support. 12. Streaming file download support. 13.
 * Bucket list as an Atom feed support. 14. Bucket list as an RSS feed support.
 * 15. Copy an item from one bucket to another. 16. Copy all items from one
 * bucket to another. 17. Putting a file with a specified content type.
 * 
 * @author Dominic Da Silva (c) 2006 SilvaSoft, Inc.
 * 
 * 
 * A simple shell-like program for interacting with S3 from the command-line.
 * This class is primary designed to interact with the user at the terminal, but
 * can read commands from any provided InputStream.
 * <p>
 * Once instantiated, this class has two modes. Initially it can be in
 * "connected" mode or "disconnected" mode. This has no direct relation on
 * whether or not a TCP connection exists to the S3 server at any point, it
 * merely refers to whether enough information has been provided by the user to
 * attempt to make a connection. Once a host, username, and password have been
 * provided through the constructor or through issuing commands, the instance
 * becomes "connected" and will begin communicating with S3 as subsequent
 * commands are issued. When the instance is "disconnected", the only commands
 * it will accept are those that don't interact with the S3 server at all. Once
 * "connected", any command may be executed.
 * <p>
 * For a list of commands accepted by the shell, see the S3Shell wiki node.
 * <p>
 * When this class is run from the command line, it will initialize the S3
 * connection parameters from the command line arguments and then begin reading
 * commands from standard input.
 * <p>
 * Instances of this class are <b>NOT</b> safe for concurrent access by
 * multiple threads.
 * 
 * @author Grant Emery (c) 2006 Amazon.com
 * 
 */
public class jSh3ll {
	/** Represents commands that may be executed when "disconnected" */
	private static final Set<String> NO_CONNECTION_COMMANDS = new HashSet<String>();
	static {
		NO_CONNECTION_COMMANDS.add("help");
		NO_CONNECTION_COMMANDS.add("quit");
		NO_CONNECTION_COMMANDS.add("exit");
		NO_CONNECTION_COMMANDS.add("host");
		NO_CONNECTION_COMMANDS.add("user");
		NO_CONNECTION_COMMANDS.add("pass");
		NO_CONNECTION_COMMANDS.add("threads");
		NO_CONNECTION_COMMANDS.add("time");
	}

	/**
	 * Set of ACL types
	 */
	private static final Set<String> ACL_TYPES = new HashSet<String>();
	static {
		ACL_TYPES.add("private");
		ACL_TYPES.add("public-read");
		ACL_TYPES.add("public-read-write");
		ACL_TYPES.add("authenticated-read");
	}

	/**
	 * Represents the different execution time reporting modes available. "LONG"
	 * commands are commands that run longer than five seconds.
	 */
	public enum TimingMode {
		NONE, LONG, ALL
	};

	/** A long representing 5GB */
	private static final Long MAX_S3_FILE_SIZE = new Long("5368709120");

	/** A long command runs for longer than one second */
	private static final int LONG_COMMAND = 1000;

	/** Number of times to attempt executing commands where we retry. */
	private static final int MAX_RETRIES = 3;

	/** Number of milliseconds to wait between retry attempts. */
	private static final int RETRY_SLEEP = 5000;

	/** Maximum number of threads allowable for multithreaded commands */
	public static final int MAX_THREADS = 20;

	/** When running "deleteall", print a period for this many deletions */
	private static final int DELETES_PER_DOT = 10;

	/** Good response */
	private static final int RESPONSE_OK = 200;

	/** Good return from bucket delete */
	private static final String NO_CONTENT = "No Content";

	/** Default Amazon S3 host */
	private static final String DEFAULT_S3_HOST = "s3.amazonaws.com";

	/** The current S3 host to connect to. */
	private String m_host = DEFAULT_S3_HOST;

	/** The current S3 user to connect as. */
	private String m_user;

	/** The current S3 password to connect with. */
	private String m_pass;

	/** The current S3 bucket to operate on. */
	private String m_bucket;

	/** The input file */
	private static String m_infile;

	/** The output file */
	private static String m_outfile;

	/** Writer */
	private BufferedReader m_reader;

	/** Reader */
	private BufferedWriter m_writer;

	/**
	 * The current prompt string displayed on the terminal before reading a line
	 * of input
	 */
	private String m_prompt;

	/** The number of threads to execute multithreaded commands with */
	private int m_threads;

	/** The current timing mode for display of command timing information */
	private TimingMode m_timingMode;

	/** The current AWSAuthConnection for communicating with S3 */
	private AWSAuthConnection m_authConn;

	/**
	 * Command-line entry point for running jSh3ll. If present, the command-line
	 * arguments will be assigned to host, username, password, and bucket in
	 * that order. The shell will then be started to read from standard input.
	 */
	public static void main(String argv[]) throws IOException {
		String host = null;
		String user = null;
		String pass = null;
		String bucket = null;
		for (int i = 0; i < argv.length; i++) {
			String arg = argv[i];
			if (arg.equals("-h")) {
				i++;
				host = argv[i];
			}
			if (arg.equals("-u")) {
				i++;
				user = argv[i];
			}
			if (arg.equals("-p")) {
				i++;
				pass = argv[i];
			}
			if (arg.equals("-b")) {
				i++;
				bucket = argv[i];
			}
			if (arg.equals("-i")) {
				i++;
				m_infile = argv[i];
			}
			if (arg.equals("-o")) {
				i++;
				m_outfile = argv[i];
			}
		}

		if (m_outfile != null && m_infile == null) {
			System.out
					.println("jSh3ll command line error: You must specify an input file (with the -i option) when an output file is specified.");
			System.exit(1);
		}

		jSh3ll shell = new jSh3ll(host, user, pass, bucket);
		shell.setPrompt("jSh3ll> ");

		try {
			InputStream instream = null;
			OutputStream outstream = null;
			if (m_infile != null) {
				instream = new FileInputStream(new File(m_infile));
			} else {
				instream = System.in;
			}
			if (m_outfile != null) {
				outstream = new FileOutputStream(new File(m_outfile));
			} else {
				outstream = System.out;
			}
			shell.processCommands(instream, outstream);
		} catch (IOException e) {
			System.out.println("jSh3ll command line error: " + e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Constructor to assign connection parameters for using S3. Any of these
	 * may be null, the shell will not allow connections to S3 or certain
	 * operations until they have been set to something non-null.
	 * 
	 * @param host
	 *            The S3 host to use [may be null]
	 * @param user
	 *            The Amazon Web Services Access Key to use [may be null]
	 * @param pass
	 *            The Amazon Web Services Secret Key ID to use [may be null]
	 * @param bucket
	 *            The S3 bucket to operate on [may be null]
	 */
	public jSh3ll(final String host, final String user, final String pass,
			final String bucket) {
		if (host != null)
			m_host = host;
		if (user != null)
			m_user = user;
		if (pass != null)
			m_pass = pass;
		if (bucket != null)
			m_bucket = bucket;

		m_prompt = "";
		m_threads = 1;
		m_timingMode = TimingMode.LONG;

		if (m_host != null && m_user != null & m_pass != null)
			initAWSAuthConnection(m_host, m_user, m_pass);
	}

	/**
	 * Initialize the Amazon S3 connection.
	 * 
	 * @param host
	 *            the S3 host
	 * @param user
	 *            the Amazon Access Key ID
	 * @param pass
	 *            the Amazon Secret Access Key
	 */
	private void initAWSAuthConnection(final String host, final String user,
			final String pass) {
		// Default to isSecure=true
		m_authConn = new AWSAuthConnection(user, pass, true, host);
	}

	/**
	 * Executes the main read-execute-print loop of the shell. Commands will be
	 * read from the given stream and results will be written to standard output
	 * and standard error.
	 * 
	 * @param commandStream
	 *            The stream of commands to read [may not be null]
	 * @throws IOException
	 *             On failure to read from the command stream
	 */
	public void processCommands(InputStream commandStream,
			OutputStream outputStream) throws IOException {

		m_reader = new BufferedReader(new InputStreamReader(commandStream));
		m_writer = new BufferedWriter(new OutputStreamWriter(outputStream));
		String line;
		writeLine("");
		writeLine("Welcome to jSh3ll (Amazon S3 command shell for Java) (c) 2006 SilvaSoft, Inc.");
		writeLine("Type 'help' for command list.");
		writeLine("");

		while ((line = getLine()) != null) {
			StringTokenizer st = new StringTokenizer(line);

			if (st.countTokens() == 0) {
				continue;
			}

			String cmd = st.nextToken();

			// If we don't have enough information to connect to S3 yet, only
			// allow a restricted subset of commands to be run.
			if (!connectedToS3Host() && !NO_CONNECTION_COMMANDS.contains(cmd)) {
				writeLine("Error: Not yet connected to S3; set host, user, and pass to continue");
				continue;
			}

			long starttime = System.currentTimeMillis();
			try {
				if (cmd.equals("bucket")) {
					if (st.countTokens() != 1) {
						writeLine("Error: bucket [name]");

						continue;
					}

					setBucket(st.nextToken());

					if (getBucket() != null) {
						writeLine("Bucket set to '" + getBucket() + "'");

					} else {
						writeLine("Error: bucket is not set");

					}
				} else if (cmd.equals("copy")) {
					if (st.countTokens() < 1 || st.countTokens() > 5) {
						writeLine("Error: copy <id> <src_bucket> <dest_bucket> [user] [password]");

						continue;
					}

					String id = st.nextToken();
					String src_bucket = st.nextToken();
					String dest_bucket = st.nextToken();

					boolean newAccount = false;

					String user = null;
					String password = null;
					if (st.countTokens() == 5) {
						newAccount = true;
						user = st.nextToken();
						password = st.nextToken();
					}

					AWSAuthConnection src_conn = new AWSAuthConnection(m_user,
							m_pass, true, m_host);
					AWSAuthConnection dest_conn = null;

					if (newAccount) {
						dest_conn = new AWSAuthConnection(user, password, true,
								m_host);
					} else {
						dest_conn = new AWSAuthConnection(m_user, m_pass, true,
								m_host);
					}

					GetStreamResponse getResponse = src_conn.getStream(
							src_bucket, id, null);
					if (RESPONSE_OK != getResponse.connection.getResponseCode()) {
						writeLine("Error: Could not find item '" + src_bucket
								+ "/" + id + "'");
					} else {
						if (RESPONSE_OK == dest_conn.putStream(dest_bucket, id,
								new S3StreamObject(getResponse.connection
										.getInputStream(), null), null).connection
								.getResponseCode()) {
							src_conn = null;
							dest_conn = null;
							writeLine("Copied '" + src_bucket + "/" + id
									+ "' to '" + dest_bucket + "/" + id + "'");
						} else {
							writeLine("Error: Could not copy '" + src_bucket
									+ "/" + id + "' to '" + dest_bucket + "/"
									+ id + "'");
						}
					}
				} else if (cmd.equals("copyall")) {
					if (st.countTokens() < 1 || st.countTokens() > 5) {
						writeLine("Error: copyall [prefix] <src_bucket> <dest_bucket> [user] [password]");

						continue;
					}

					boolean newAccount = false;

					String prefix = null;
					String src_bucket = null;
					String dest_bucket = null;
					String user = null;
					String password = null;

					if (st.countTokens() == 5) {
						newAccount = true;
						prefix = st.nextToken();
						src_bucket = st.nextToken();
						dest_bucket = st.nextToken();
						user = st.nextToken();
						password = st.nextToken();
					} else if (st.countTokens() == 4) {
						newAccount = true;
						src_bucket = st.nextToken();
						dest_bucket = st.nextToken();
						user = st.nextToken();
						password = st.nextToken();
					} else if (st.countTokens() == 3) {
						prefix = st.nextToken();
						src_bucket = st.nextToken();
						dest_bucket = st.nextToken();
					} else if (st.countTokens() == 2) {
						src_bucket = st.nextToken();
						dest_bucket = st.nextToken();
					} else {
						writeLine("Error: copyall [prefix] <src_bucket> <dest_bucket> [user] [password]");

						continue;
					}

					AWSAuthConnection src_conn = new AWSAuthConnection(m_user,
							m_pass, true, m_host);
					AWSAuthConnection dest_conn = null;

					if (newAccount) {
						dest_conn = new AWSAuthConnection(user, password, true,
								m_host);
					} else {
						dest_conn = new AWSAuthConnection(m_user, m_pass, true,
								m_host);
					}

					List<ListEntry> ids = new ArrayList<ListEntry>();
					String lastid = null;
					int max = 0;
					while (true) {
						List<ListEntry> results = src_conn.listBucket(
								src_bucket, prefix, lastid, null, null).entries;

						if (results == null || results.size() == 0) {
							break;
						}
						lastid = ((ListEntry) results.get(results.size() - 1)).key;
						ids.addAll(results);
						if (ids.size() == max) {
							break;
						}
					}
					String displayName = "";
					for (ListEntry id : ids) {
						GetStreamResponse getResponse = src_conn.getStream(
								src_bucket, id.key, null);
						if (RESPONSE_OK != getResponse.connection
								.getResponseCode()) {
							writeLine("Error: Could not find item '"
									+ src_bucket + "/" + id + "'");
						} else {
							if (RESPONSE_OK == dest_conn.putStream(dest_bucket,
									id.key, new S3StreamObject(
											getResponse.connection
													.getInputStream(), null),
									null).connection.getResponseCode()) {
								writeLine("Copied '" + src_bucket + "/" + id
										+ "' to '" + dest_bucket + "/" + id
										+ "'");
							} else {
								writeLine("Error: Could not copy '"
										+ src_bucket + "/" + id + "' to '"
										+ dest_bucket + "/" + id + "'");
							}
						}
					}
					src_conn = null;
					dest_conn = null;
				} else if (cmd.equals("count")) {
					if (st.countTokens() > 1) {
						writeLine("Error: count [prefix]");

						continue;
					}

					String prefix = null;
					if (st.countTokens() > 0) {
						prefix = st.nextToken();
					}

					int count = countItems(prefix);

					// On error, countItems returns < 0
					if (count >= 0) {
						writeLine(count + " item(s)");

					}
				} else if (cmd.equals("createbucket")) {
					if (st.countTokens() != 0 && st.countTokens() != 1) {
						writeLine("Error: createbucket [private|public-read|public-read-write|authenticated-read]");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String acl = "";
					Map<String, List<String>> headers = null;
					if (st.countTokens() == 1) {
						acl = st.nextToken();
						if (!ACL_TYPES.contains(acl)) {
							writeLine("Error: invalid ACL type");

							continue;
						}
						headers = new LinkedHashMap<String, List<String>>();
						List<String> headerList = new ArrayList<String>();
						headerList.add(acl);
						headers.put("x-amz-acl", headerList);
					}

					Response response = m_authConn.createBucket(m_bucket,
							headers);
					if (RESPONSE_OK == response.connection.getResponseCode()) {
						writeLine("Created bucket '" + m_bucket + "'");

					} else {
						writeLine(response.connection.getResponseMessage());

					}
				} else if (cmd.equals("delete")) {
					if (st.countTokens() != 1) {
						writeLine("Error: delete <id>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String id = st.nextToken();

					Response response = m_authConn.delete(m_bucket, id, null);
					if (NO_CONTENT.equals(response.connection
							.getResponseMessage())) {
						writeLine("Deleted '" + m_bucket + "/" + id + "'");

					} else {
						writeLine(response.connection.getResponseMessage());

					}
				} else if (cmd.equals("deleteall")) {
					if (st.countTokens() > 1) {
						writeLine("Error: deleteall [prefix]");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String prefix = null;
					if (st.countTokens() > 0) {
						prefix = st.nextToken();
					}

					List<ListEntry> items = new ArrayList<ListEntry>();
					String lastid = null;
					while (true) {
						List<ListEntry> results = m_authConn.listBucket(
								m_bucket, prefix, lastid, null, null).entries;

						if (results == null || results.size() == 0) {
							break;
						}
						lastid = ((ListEntry) results.get(results.size() - 1)).key;
						items.addAll(results);
					}

					int deletecount = 0;
					int nodeletecount = 0;
					if (items != null && !items.isEmpty()) {
						for (ListEntry item : items) {
							if (NO_CONTENT.equals(m_authConn.delete(m_bucket,
									item.key, null).connection
									.getResponseMessage())) {
								deletecount++;
							} else {
								nodeletecount++;
							}
						}
						writeLine("Deleted " + deletecount
								+ " item(s), could not delete " + nodeletecount
								+ " item(s)");
					} else {
						writeLine("No items in bucket '" + m_bucket + "'");
					}
				} else if (cmd.equals("deletebucket")) {
					if (st.countTokens() != 0) {
						writeLine("Error: deletebucket");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					Response response = m_authConn.deleteBucket(m_bucket, null);
					if (NO_CONTENT.equals(response.connection
							.getResponseMessage())) {
						writeLine("Deleted bucket '" + m_bucket + "'");
						m_bucket = null;

					} else {
						writeLine(response.connection.getResponseMessage());

					}
				} else if (cmd.equals("exit") || cmd.equals("quit")) {
					writeLine("Goodbye");
					return;
				} else if (cmd.equals("get")) {
					if (st.countTokens() != 1) {
						writeLine("Error: get <id>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String id = st.nextToken();
					byte[] data = m_authConn.get(m_bucket, id, null).object.data;

					if (data == null) {
						writeLine("Item '" + id + "' not found");

					} else {
						writeLine(new String(data));

					}
				} else if (cmd.equals("getacl")) {
					if (st.countTokens() != 2) {
						writeLine("Error: getacl [bucket|item] <id>");

						continue;
					}

					String objectType = "";
					String id = "";
					if (st.hasMoreElements()) {
						objectType = st.nextToken();
						id = st.nextToken();
					}

					byte[] data = null;
					S3Object s3Object = null;

					if (objectType.equals("bucket")) {
						s3Object = m_authConn.getBucketACL(id, null).object;
						if (s3Object == null) {
							writeLine("Error: bucket '" + id + "' not found");

							continue;
						}
						data = s3Object.data;
					} else if (objectType.equals("item")) {
						if (m_bucket == null) {
							writeLine("Error: bucket is not set");

							continue;
						}
						s3Object = m_authConn.getACL(m_bucket, id, null).object;
						if (s3Object == null) {
							writeLine("Error: item '" + m_bucket + "/" + id
									+ "' not found");

							continue;
						}
						data = s3Object.data;
					} else {
						writeLine("Error: getacl [bucket|item] <id>");

						continue;
					}

					if (data == null) {
						if (objectType.equals("item")) {
							writeLine("Error: item '" + m_bucket + "/" + id
									+ "' not found");

						} else if (objectType.equals("bucket")) {
							writeLine("Error: bucket '" + id + "' not found");

						}
					} else {
						writeLine(new String(data));

					}
				} else if (cmd.equals("getfile")) {
					if (st.countTokens() != 2) {
						writeLine("Error: getfile <id> <file>");

						continue;
					}

					String id = st.nextToken();
					String filename = st.nextToken();

					S3StreamObject s3Object = null;

					s3Object = m_authConn.getStream(m_bucket, id, null).object;
					if (s3Object.stream == null) {
						writeLine("Error: item '" + m_bucket + "/" + id
								+ "' not found");

						continue;
					}
					FileOutputStream datafile = new FileOutputStream(filename);
					try {
						byte[] buf = new byte[1024];
						int bytesRead = 0;

						while ((bytesRead = s3Object.stream.read(buf)) > 0) {
							datafile.write(buf, 0, bytesRead);
						}
						s3Object.stream.close();

					} finally {
						datafile.close();
					}
					writeLine("Got item '" + m_bucket + "/" + id + "' as '"
							+ filename + "'");
				} else if (cmd.equals("getfilez")) {
					if (st.countTokens() != 2) {
						writeLine("Error: getfilez <id> <file>");

						continue;
					}

					String id = st.nextToken();
					String filename = st.nextToken();
					byte[] dataGzipped = m_authConn.get(m_bucket, id, null).object.data;

					byte[] data = S3Helper.decompressBytes(dataGzipped);

					if (data == null) {
						writeLine("Error: item '" + m_bucket + "/" + id
								+ "' not found");

					} else {
						FileOutputStream datafile = new FileOutputStream(
								filename);
						try {
							datafile.write(data);
						} finally {
							datafile.close();
						}
						writeLine("Got item '" + m_bucket + "/" + id + "' as '"
								+ filename + "'");

					}
				} else if (cmd.equals("gettorrent")) {
					if (st.countTokens() != 1) {
						writeLine("Error: gettorrent <id>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String id = st.nextToken();
					byte[] data = m_authConn.getTorrent(m_bucket, id, null).object.data;

					if (data == null) {
						writeLine("Error: item '" + m_bucket + "/" + id
								+ "' not found");

					} else {
						FileOutputStream datafile = new FileOutputStream(id
								+ ".torrent");
						try {
							datafile.write(data);
						} finally {
							datafile.close();
						}
						writeLine("Got torrent '" + id + ".torrent" + "'");

					}
				} else if (cmd.equals("head")) {
					if (st.countTokens() != 2) {
						writeLine("Error: head [bucket|item] <id>");

						continue;
					}

					if (st.hasMoreTokens()) {
						String objectType = st.nextToken();
						String id = st.nextToken();

						String metadata = null;
						S3Object s3Object = null;

						if (objectType.equals("item")) {
							if (m_bucket == null) {
								writeLine("Error: bucket is not set");

								continue;
							}
							s3Object = m_authConn.get(m_bucket, id, null).object;
							if (s3Object != null) {
								metadata = s3Object.metadata.toString();
							} else {
								writeLine("Error: item '" + m_bucket + "/" + id
										+ "' not found");

								continue;
							}
							writeLine("Head '" + m_bucket + "/" + id + "' = "
									+ metadata);

						}

						if (objectType.equals("bucket")) {
							s3Object = m_authConn.get(id, null, null).object;
							if (s3Object != null) {
								metadata = s3Object.metadata.toString();
							} else {
								writeLine("Error: bucket '" + id
										+ "' not found");

								continue;
							}
							writeLine("Head bucket '" + id + "' = " + metadata);

						}
					}
				} else if (cmd.equals("help")) {
					printHelp();
				} else if (cmd.equals("host")) {
					if (st.countTokens() > 1) {
						writeLine("Error: host [hostname]");

						continue;
					}

					if (st.hasMoreTokens()) {
						setHost(st.nextToken());
						if (m_host != null && m_user != null & m_pass != null)
							initAWSAuthConnection(m_host, m_user, m_pass);
					} else {
						if (getHost() != null) {
							writeLine("host = " + getHost());

						} else {
							writeLine("Error: host is not set");

						}
					}
				} else if (cmd.equals("list")) {
					if (st.countTokens() > 2) {
						writeLine("Error: list [prefix] [max]");

					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String prefix = null;
					int max = 0;
					if (st.hasMoreTokens()) {
						prefix = st.nextToken();
					}
					if (st.hasMoreTokens()) {
						try {
							max = Integer.parseInt(st.nextToken());
						} catch (NumberFormatException e) {
							writeLine(e.getMessage());

							continue;
						}
						if (max < 0) {
							writeLine("Error: max must be >= 0");

							continue;
						}
					}

					Integer maxKeys = null;
					if (max > 0) {
						maxKeys = new Integer(max);
					}

					if (prefix != null && prefix.equals("*")) {
						prefix = null;
					}

					writeLine("Item list for bucket '" + m_bucket + "'");
					List<ListEntry> ids = new ArrayList<ListEntry>();
					String lastid = null;
					while (true) {
						List<ListEntry> results = m_authConn.listBucket(
								m_bucket, prefix, lastid, maxKeys, null).entries;

						if (results == null || results.size() == 0) {
							break;
						}
						lastid = ((ListEntry) results.get(results.size() - 1)).key;
						ids.addAll(results);
						if (ids.size() == max) {
							break;
						}
					}
					String displayName = "";
					for (ListEntry id : ids) {
						displayName = id.owner != null ? id.owner.displayName
								: "unknown";
						writeLine("key=" + id.key + ", owner=" + displayName
								+ ", size=" + id.size
								+ " bytes, last modified=" + id.lastModified);
					}
				} else if (cmd.equals("listrss")) {
					if (st.countTokens() > 2) {
						writeLine("Error: listrss [prefix] [max]");

					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String prefix = null;
					int max = 0;
					if (st.hasMoreTokens()) {
						prefix = st.nextToken();
					}
					if (st.hasMoreTokens()) {
						try {
							max = Integer.parseInt(st.nextToken());
						} catch (NumberFormatException e) {
							writeLine(e.getMessage());

							continue;
						}
						if (max < 0) {
							writeLine("Error: max must be >= 0");

							continue;
						}
					}

					Integer maxKeys = null;
					if (max > 0) {
						maxKeys = new Integer(max);
					}

					if (prefix != null && prefix.equals("*")) {
						prefix = null;
					}

					writeLine("Item list for bucket '" + m_bucket + "'");
					List<ListEntry> ids = new ArrayList<ListEntry>();
					String lastid = null;
					while (true) {
						List<ListEntry> results = m_authConn.listBucket(
								m_bucket, prefix, lastid, maxKeys, null).entries;

						if (results == null || results.size() == 0) {
							break;
						}
						lastid = ((ListEntry) results.get(results.size() - 1)).key;
						ids.addAll(results);
						if (ids.size() == max) {
							break;
						}
					}
					String rssDoc = createRSSDocument(ids);
					writeLine(rssDoc);
				} else if (cmd.equals("listatom")) {
					if (st.countTokens() > 2) {
						writeLine("Error: listatom [prefix] [max]");

					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String prefix = null;
					int max = 0;
					if (st.hasMoreTokens()) {
						prefix = st.nextToken();
					}
					if (st.hasMoreTokens()) {
						try {
							max = Integer.parseInt(st.nextToken());
						} catch (NumberFormatException e) {
							writeLine(e.getMessage());

							continue;
						}
						if (max < 0) {
							writeLine("Error: max must be >= 0");

							continue;
						}
					}

					Integer maxKeys = null;
					if (max > 0) {
						maxKeys = new Integer(max);
					}

					if (prefix != null && prefix.equals("*")) {
						prefix = null;
					}

					writeLine("Item list for bucket '" + m_bucket + "'");
					List<ListEntry> ids = new ArrayList<ListEntry>();
					String lastid = null;
					while (true) {
						List<ListEntry> results = m_authConn.listBucket(
								m_bucket, prefix, lastid, maxKeys, null).entries;

						if (results == null || results.size() == 0) {
							break;
						}
						lastid = ((ListEntry) results.get(results.size() - 1)).key;
						ids.addAll(results);
						if (ids.size() == max) {
							break;
						}
					}
					String atomDoc = createAtomDocument(ids);
					writeLine(atomDoc);
				} else if (cmd.equals("listbuckets")) {
					if (st.countTokens() != 0) {
						writeLine("Error: listbuckets");

						continue;
					}

					List<Bucket> buckets = m_authConn.listAllMyBuckets(null).entries;
					if (buckets != null) {
						for (Bucket bucket : buckets) {
							writeLine(bucket.name + " - " + bucket.creationDate);

						}
					}
				} else if (cmd.equals("pass")) {
					if (st.countTokens() > 1) {
						writeLine("Error: pass [username]");

						continue;
					}

					if (st.hasMoreTokens()) {
						setPass(st.nextToken());
						if (m_host != null && m_user != null & m_pass != null)
							initAWSAuthConnection(m_host, m_user, m_pass);
					} else {
						if (getPass() != null) {
							writeLine("pass = " + getPass());

						} else {
							writeLine("Error: pass is not set");

						}
					}
				} else if (cmd.equals("put")) {
					if (st.countTokens() < 2) {
						writeLine("Error: put <id> <data>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String id = st.nextToken();
					String restOfLine = line.substring(line.indexOf(id)
							+ id.length() + 1);

					Response response = m_authConn.put(m_bucket, id,
							new S3Object(restOfLine.getBytes(), null), null);
					if (RESPONSE_OK == response.connection.getResponseCode()) {
						writeLine("Stored item '" + m_bucket + "/" + id + "'");

					} else {
						writeLine("Error: unable to store item '" + m_bucket
								+ "/" + id + "'");

						writeLine(response.connection.getResponseMessage());

					}
				} else if (cmd.equals("putdir")) {
					if (st.countTokens() < 1) {
						writeLine("Error: putdir <dir>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					boolean ok = false;
					String dirname = st.nextToken();

					// get a directory list
					File dir = new File(dirname);
					String[] files = dir.list();
					for (String file : files) {
						String id = file;
						ok = putFileStream(id, dir.getAbsolutePath()
								+ File.separatorChar + file, null);
						if (!ok) {
							writeLine("Error: unable to store item '"
									+ m_bucket + "/" + id + "'");
							continue;
						} else {
							writeLine("Stored item '" + m_bucket + "/" + id
									+ "'");
							continue;
						}
					}
				} else if (cmd.equals("putdirwacl")) {
					if (st.countTokens() < 2) {
						writeLine("Error: putdir <dir> [private|public-read|public-read-write|authenticated-read]");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					boolean ok = false;
					String dirname = st.nextToken();
					String acl = st.nextToken();

					if (!ACL_TYPES.contains(acl)) {
						writeLine("Error: invalid ACL type");

						continue;
					}

					// get a directory list
					File dir = new File(dirname);
					String[] files = dir.list();
					for (String file : files) {
						String id = file;
						ok = putFileStream(id, dir.getAbsolutePath()
								+ File.separatorChar + file, acl);
						if (!ok) {
							writeLine("Error: unable to store item '"
									+ m_bucket + "/" + id + "'");
							continue;
						} else {
							writeLine("Stored item '" + m_bucket + "/" + id
									+ "'");
							continue;
						}
					}
				} else if (cmd.equals("putfile")) {
					if (st.countTokens() != 2) {
						writeLine("Error: putfile <id> <file>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket not set");

						continue;
					}

					String id = st.nextToken();
					String file = st.nextToken();
					boolean ok = false;
					ok = putFileStream(id, file, null);

					if (!ok) {
						writeLine("Error: unable to store item '" + m_bucket
								+ "/" + id + "'");

					} else {
						writeLine("Stored item '" + m_bucket + "/" + id + "'");

					}
				} else if (cmd.equals("putfilez")) {
					if (st.countTokens() != 2) {
						writeLine("Error: putfilez <id> <file>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					String id = st.nextToken();
					String file = st.nextToken();
					boolean ok = false;
					ok = putFile(id, file, null, true);

					if (!ok) {
						writeLine("Error: unable to store item '" + m_bucket
								+ "/" + id + "'");

					} else {
						writeLine("Stored item '" + m_bucket + "/" + id + "'");

					}
				} else if (cmd.equals("putfilewacl")) {
					if (st.countTokens() != 3) {
						writeLine("Error: putfilewacl <id> <file> [private|public-read|public-read-write|authenticated-read]");
						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");
						continue;
					}

					boolean ok = false;
					String id = st.nextToken();
					String file = st.nextToken();
					String acl = st.nextToken();

					if (!ACL_TYPES.contains(acl)) {
						writeLine("Error: invalid ACL type");

						continue;
					}

					ok = putFileStream(id, file, acl);

					if (!ok) {
						writeLine("Error: unable to store item '" + m_bucket
								+ "/" + id + "'");

					} else {
						writeLine("Stored item '" + m_bucket + "/" + id + "'");

					}
				} else if (cmd.equals("putfilezwacl")) {
					if (st.countTokens() != 3) {
						writeLine("Error: putfilezwacl <id> <file> [private|public-read|public-read-write|authenticated-read]");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket is not set");

						continue;
					}

					boolean ok = false;
					String id = st.nextToken();
					String file = st.nextToken();
					String acl = st.nextToken();

					if (!ACL_TYPES.contains(acl)) {
						writeLine("Error: invalid ACL type");

						continue;
					}

					ok = putFile(id, file, acl, true);

					if (!ok) {
						writeLine("Error: unable to store item '" + m_bucket
								+ "/" + id + "'");

					} else {
						writeLine("Stored item '" + m_bucket + "/" + id + "'");

					}
				} else if (cmd.equals("putfilecontenttype")) {
					if (st.countTokens() != 3) {
						writeLine("Error: putfile <id> <file> <content-type>");

						continue;
					}

					if (m_bucket == null) {
						writeLine("Error: bucket not set");

						continue;
					}

					String id = st.nextToken();
					String file = st.nextToken();
					String contentType = st.nextToken();
					boolean ok = false;
					ok = putFileStreamContentType(id, file, null, contentType);

					if (!ok) {
						writeLine("Error: unable to store item '" + m_bucket
								+ "/" + id + "'");

					} else {
						writeLine("Stored item '" + m_bucket + "/" + id + "' with content type " + contentType);

					}
				} else if (cmd.equals("setacl")) {
					if (st.countTokens() != 3) {
						writeLine("Error: setacl [bucket|item] <id> [private|public-read|public-read-write|authenticated-read]");

						continue;
					}

					String objectType = "";
					String id = "";
					String acl = "";

					if (st.hasMoreTokens()) {
						objectType = st.nextToken();
						id = st.nextToken();
						acl = st.nextToken();
					}

					if (objectType.equals("bucket")) {
						S3Object s3Object = null;
						s3Object = m_authConn.getACL(id, null, null).object;
						if (s3Object == null) {
							writeLine("Error: bucket '" + id + "' not found");

							continue;
						}
						String currentACL = new String(s3Object.data);
						// writeLine(currentACL);
						String currenId = currentACL.substring(currentACL
								.indexOf("<ID>") + 4, currentACL
								.indexOf("</ID>"));

						String aclXmlDoc = "";
						if (acl.equals("private")) {
							aclXmlDoc = this.getACLTemplatePrivate(currenId);
						} else if (acl.equals("public-read")) {
							aclXmlDoc = this.getACLTemplatePublicRead(currenId);
						} else if (acl.equals("public-read-write")) {
							aclXmlDoc = this
									.getACLTemplatePublicReadWrite(currenId);
						} else {
							writeLine(acl + " not supported at this time.");

							continue;
						}
						// writeLine("aclXmlDoc: " + aclXmlDoc);

						Response response = m_authConn.putBucketACL(id,
								aclXmlDoc, null);
						if (RESPONSE_OK == response.connection
								.getResponseCode()) {
							writeLine("Set ACL for bucket '" + id + "' to "
									+ acl);

						} else {
							writeLine("Error: could not set ACL for bucket '"
									+ id + "' to " + acl);

							writeLine(response.connection.getResponseMessage());

						}
					} else if (objectType.equals("item")) {
						if (m_bucket == null) {
							writeLine("Error: bucket is not set");

							continue;
						}
						S3Object s3Object = null;
						s3Object = m_authConn.getACL(m_bucket, id, null).object;
						if (s3Object == null) {
							writeLine("Error: item '" + m_bucket + "/" + id
									+ "' not found");

							continue;
						}
						String currentACL = new String(s3Object.data);
						// writeLine(currentACL);
						String currentId = currentACL.substring(currentACL
								.indexOf("<ID>") + 4, currentACL
								.indexOf("</ID>"));

						String aclXmlDoc = "";
						if (acl.equals("private")) {
							aclXmlDoc = this.getACLTemplatePrivate(currentId);
						} else if (acl.equals("public-read")) {
							aclXmlDoc = this
									.getACLTemplatePublicRead(currentId);
						} else if (acl.equals("public-read-write")) {
							aclXmlDoc = this
									.getACLTemplatePublicReadWrite(currentId);
						} else {
							writeLine(acl + " not supported at this time.");

							continue;
						}
						// writeLine("aclXmlDoc: " + aclXmlDoc);

						Response response = m_authConn.putACL(m_bucket, id,
								aclXmlDoc, null);
						if (RESPONSE_OK == response.connection
								.getResponseCode()) {
							writeLine("Set ACL for item '" + m_bucket + "/"
									+ id + "' to " + acl);

						} else {
							writeLine("Error: could not set ACL for item '"
									+ m_bucket + "/" + id + "' to " + acl);

							writeLine(response.connection.getResponseMessage());

						}
					} else {
						writeLine("Error: setacl [bucket|item] <id> [private|public-read|public-read-write|authenticated-read]");

						continue;
					}
				} else if (cmd.equals("threads")) {
					if (st.countTokens() > 1) {
						writeLine("Error: threads [num]");

						continue;
					}

					if (st.hasMoreTokens()) {
						try {
							setThreads(Integer.parseInt(st.nextToken()));
						} catch (NumberFormatException e) {
							writeLine(e.getMessage());

						}
					} else {
						writeLine("threads = " + getThreads());

					}
				} else if (cmd.equals("time")) {
					if (st.countTokens() > 1) {
						writeLine("Error: time [none|long|all]");

						continue;
					}

					if (st.hasMoreTokens()) {
						final String mode = st.nextToken();
						try {
							setTimingMode(TimingMode.valueOf(TimingMode.class,
									mode.toUpperCase()));
						} catch (IllegalArgumentException e) {
							writeLine("Error: time [none|long|all]");

						}
					} else {
						writeLine("time = " + getTimingMode());

					}
				} else if (cmd.equals("user")) {
					if (st.countTokens() > 1) {
						writeLine("Error: user [username]");

						continue;
					}

					if (st.hasMoreTokens()) {
						setUser(st.nextToken());
						if (m_host != null && m_user != null & m_pass != null)
							initAWSAuthConnection(m_host, m_user, m_pass);
					} else {
						if (getUser() != null) {
							writeLine("user = " + getUser());

						} else {
							writeLine("Error: user is not set");

						}
					}
				} else {
					writeLine("Error: unknown command");
				}
			} catch (Exception e) {
				writeLine(e.getMessage());
			}

			long runtime = System.currentTimeMillis() - starttime;
			if (m_timingMode == TimingMode.ALL
					|| m_timingMode == TimingMode.LONG
					&& runtime > LONG_COMMAND) {
				writeLine("[runtime: " + formatRuntime(runtime) + "]");
			}
		}
	}

	private void writeLine(String line) throws IOException {
		m_writer.write(line);
		m_writer.write("\n");
		m_writer.flush();
	}

	/**
	 * Returns the current timing mode of the shell.
	 * 
	 * @return The current timing mode of the shell [not null].
	 * @see TimingMode
	 */
	public TimingMode getTimingMode() {
		return m_timingMode;
	}

	/**
	 * Sets the current timing mode of the shell.
	 * 
	 * @param timingMode
	 *            The timing mode to use [may not be null]
	 * @see TimingMode
	 */
	public void setTimingMode(final TimingMode timingMode) {
		if (timingMode == null)
			throw new IllegalArgumentException("timingMode may not be null");

		m_timingMode = timingMode;
	}

	/**
	 * Gets the number of threads used by the shell to execute multithreaded
	 * commands.
	 * 
	 * @return The number of threads [between 1 and MAX_THREADS, inclusive]
	 * @see #MAX_THREADS
	 */
	public int getThreads() {
		return m_threads;
	}

	/**
	 * Sets the number of threads used by the shell to execute multithreaded
	 * commands.
	 * 
	 * @param threads
	 *            The number of threads [between 1 and MAX_THREADS, inclusive]
	 * @see #MAX_THREADS
	 */
	public void setThreads(final int threads) {
		if (threads < 1 || threads > MAX_THREADS) {
			throw new IllegalArgumentException(
					"number of threads must be between 1 and " + MAX_THREADS);
		}
		m_threads = threads;
	}

	/**
	 * Gets the prompt displayed to the user before reading a line of input.
	 * 
	 * @return The current prompt [not null]
	 */
	public String getPrompt() {
		return m_prompt;
	}

	/**
	 * Sets the prompt displayed to the user before reading a line of input.
	 * 
	 * @param prompt
	 *            The prompt to display [not null]
	 */
	public void setPrompt(final String prompt) {
		if (prompt == null)
			throw new IllegalArgumentException("prompt may not be null");

		m_prompt = prompt;
	}

	/**
	 * Gets the current S3 host used to execute commands.
	 * 
	 * @return The current host [may be null]
	 */
	public String getHost() {
		return m_host;
	}

	/**
	 * Sets the current S3 host used to execute commands.
	 * 
	 * @param host
	 *            The S3 host [may be null]
	 */
	public void setHost(final String host) {
		m_host = host;
	}

	/**
	 * Gets the current AWS Access Key ID to use to connect to S3.
	 * 
	 * @return The current Access Key ID [may be null]
	 */
	public String getUser() {
		return m_user;
	}

	/**
	 * Sets the current AWS Access Key ID to use to connect to S3.
	 * 
	 * @param user
	 *            The Access Key ID [may be null]
	 */
	public void setUser(final String user) {
		m_user = user;
	}

	/**
	 * Gets the current AWS Secret Access Key to use to connect to S3.
	 * 
	 * @return The current AWS Secret Access Key [may be null]
	 */
	public String getPass() {
		return m_pass;
	}

	/**
	 * Sets the AWS Secret Access Key to use to connect to S3.
	 * 
	 * @param pass
	 *            The Secret Access Key [may be null]
	 */
	public void setPass(final String pass) {
		m_pass = pass;
	}

	/**
	 * Gets the current S3 bucket.
	 * 
	 * @return The current S3 bucket [may be null]
	 */
	public String getBucket() {
		return m_bucket;
	}

	/**
	 * Sets the current S3 bucket.
	 * 
	 * @param bucket
	 *            The bucket to use [may be null]
	 */
	public void setBucket(final String bucket) {
		m_bucket = bucket;
	}

	/**
	 * Reads the next line from the given reader. Before reading the line, the
	 * current prompt will be printed to System.out.
	 * 
	 * @param br
	 *            The BufferedReader to read from [may not be null]
	 * @see #setPrompt(String)
	 * @see #getPrompt()
	 */
	private String getLine() throws IOException {
		m_writer.write(m_prompt);
		m_writer.flush();
		return m_reader.readLine();
	}

	/**
	 * Converts a length of time in milliseconds into a nicely formatted string
	 * representing hours, minutes, seconds, and milliseconds.
	 * <p>
	 * For example, <tt>43384005</tt> becomes <tt>12h03m04.005s</tt>.
	 * 
	 * @param runtime
	 *            The time to format
	 * @return A string representation of the time suitable for user display
	 */
	private String formatRuntime(long runtime) {
		final StringBuilder timestr = new StringBuilder();
		// First compute the number of hours
		if (runtime >= 60 * 60 * 1000) {
			timestr.append(runtime / (60 * 60 * 1000)).append("h");
			runtime %= 60 * 60 * 1000;
		}

		// If there was a non-zero number of hours, or if there are minutes to
		// include, we need to format the number of minutes. If there are
		// hours, we need to use a zero-padded number of minutes.
		if (runtime >= 60 * 1000 || timestr.length() > 0) {
			String mins;
			if (timestr.length() > 0) {
				timestr.append(String.format("%02d", runtime / (60 * 1000)));
			} else {
				timestr.append(runtime / (60 * 1000));
			}
			timestr.append("m");

			runtime %= 60 * 1000;
		}

		// Similarly for the number of seconds, but even if the runtime is
		// less than one second, we want to at least include a zero before the
		// decimal point.
		if (timestr.length() > 0) {
			timestr.append(String.format("%02d", runtime / 1000));
		} else {
			timestr.append(runtime / 1000);
		}
		timestr.append(".");

		runtime %= 1000;

		// And finally, the milliseconds, always zero-padded.
		timestr.append(String.format("%03d", runtime)).append("s");

		return timestr.toString();
	}

	/**
	 * Helper method to store a the contents of a file into S3 under a given ID.
	 * 
	 * @param id
	 *            The S3 ID to store the contents of the file under [may not be
	 *            null]
	 * @param file
	 *            The name of the file to read [may not be null]
	 * @param acl
	 *            The ACL of the file (private, public-read, public-read-write,
	 *            authenticated-read)
	 * @throws IOException
	 *             Thrown by the file operations or S3 on error.
	 */
	private boolean putFile(final String id, final String file,
			final String acl, final boolean compression) throws IOException {

		Map<String, List<String>> headers = null;

		if (acl != null) {
			headers = new LinkedHashMap<String, List<String>>();
			List<String> headerList = new ArrayList<String>();
			headerList.add(acl);
			headers.put("x-amz-acl", headerList);
		}

		File datafile = new File(file);
		if (datafile.length() > Integer.MAX_VALUE) {
			writeLine(datafile + " is too large");
			return false;
		}
		byte[] buf = new byte[(int) datafile.length()];

		DataInputStream dis = new DataInputStream(new FileInputStream(datafile));
		try {
			dis.readFully(buf);
		} finally {
			dis.close();
		}

		S3Object s3Object = null;
		if (compression) {
			byte[] gzipBuf = S3Helper.compressBytes(buf);
			s3Object = new S3Object(gzipBuf, null);
		} else {
			s3Object = new S3Object(buf, null);
		}

		boolean ok = false;
		if (RESPONSE_OK == m_authConn.put(m_bucket, id, s3Object, headers).connection
				.getResponseCode())
			ok = true;

		return ok;
	}

	/**
	 * Helper method to store a the contents of a file stream into S3 under a
	 * given ID.
	 * 
	 * @param id
	 *            The S3 ID to store the contents of the file under [may not be
	 *            null]
	 * @param file
	 *            The name of the file to read [may not be null]
	 * @param acl
	 *            The ACL of the file (private, public-read, public-read-write,
	 *            authenticated-read)
	 * @throws IOException
	 *             Thrown by the file operations or S3 on error.
	 */
	private boolean putFileStream(final String id, final String file,
			final String acl) throws IOException {

		Map<String, List<String>> headers = null;

		if (acl != null) {
			headers = new LinkedHashMap<String, List<String>>();
			List<String> headerList = new ArrayList<String>();
			headerList.add(acl);
			headers.put("x-amz-acl", headerList);
		}

		File datafile = new File(file);
		if (Long.valueOf("" + datafile.length()).compareTo(MAX_S3_FILE_SIZE) > 0) {
			writeLine(file + " is too large to be stored on S3.");
			writeLine("Maximum S3 file size is " + MAX_S3_FILE_SIZE
					+ " bytes, " + file + " is " + datafile.length() + " bytes");
			return false;
		}
		DataInputStream dis = new DataInputStream(new FileInputStream(datafile));
		S3StreamObject s3Object = new S3StreamObject(dis, null);
		s3Object.length = datafile.length();

		boolean ok = false;
		if (RESPONSE_OK == m_authConn
				.putStream(m_bucket, id, s3Object, headers).connection
				.getResponseCode())
			ok = true;

		dis.close();

		return ok;
	}

	/**
 	 * Helper method to store a the contents of a file stream into S3 under a
	 * given ID and with a given content type.
	 * 
	 * @param id
	 *            The S3 ID to store the contents of the file under [may not be
	 *            null]
	 * @param file
	 *            The name of the file to read [may not be null]
	 * @param acl
	 *            The ACL of the file (private, public-read, public-read-write,
	 *            authenticated-read)
	 * @param contentType 
	 * 			  The content type of the file          
	 * @throws IOException
	 *             Thrown by the file operations or S3 on error.
	 */
	private boolean putFileStreamContentType(final String id,
			final String file, final String acl, String contentType)
			throws IOException {

		Map<String, List<String>> headers = null;

		if (acl != null) {
			headers = new LinkedHashMap<String, List<String>>();
			List<String> headerList = new ArrayList<String>();
			headerList.add(acl);
			headers.put("x-amz-acl", headerList);
		}

		File datafile = new File(file);
		if (Long.valueOf("" + datafile.length()).compareTo(MAX_S3_FILE_SIZE) > 0) {
			writeLine(file + " is too large to be stored on S3.");
			writeLine("Maximum S3 file size is " + MAX_S3_FILE_SIZE
					+ " bytes, " + file + " is " + datafile.length() + " bytes");
			return false;
		}
		DataInputStream dis = new DataInputStream(new FileInputStream(datafile));

		Map<String, List<String>> metadata = new TreeMap<String, List<String>>();
		metadata.put("Content-Type", java.util.Arrays
				.asList(new String[] { contentType }));

		S3StreamObject s3Object = new S3StreamObject(dis, metadata);
		s3Object.length = datafile.length();

		boolean ok = false;
		if (RESPONSE_OK == m_authConn
				.putStream(m_bucket, id, s3Object, headers).connection
				.getResponseCode())
			ok = true;

		dis.close();

		return ok;
	}

	/**
	 * Helper method to iteratively count the items in a bucket.
	 * 
	 * @param prefix
	 *            If non-null, only items with IDs start with this prefix are
	 *            counted [may be null]
	 * @return The number of matching items. On error, a negative number will be
	 *         returned.
	 */
	private int countItems(final String prefix) throws IOException {
		String lastid = prefix;
		int count = 0;
		List ids;
		while (true) {
			ids = m_authConn.listBucket(m_bucket, prefix, lastid, null, null).entries;

			if (ids == null) {
				writeLine("Error: unable to count bucket contents");
				return -1;
			} else if (ids.size() == 0) {
				break;
			}

			count += ids.size();
			lastid = ((ListEntry) ids.get(ids.size() - 1)).key;
		}

		return count;
	}

	/**
	 * Helper method to print the help message.
	 */
	private void printHelp() throws IOException {
		writeLine("bucket [bucketname]");
		writeLine("copy <id> <src_bucket> <dest_bucket> [user] [password]");
		writeLine("copyall [prefix] <src_bucket> <dest_bucket> [user] [password]");
		writeLine("count [prefix]");
		writeLine("createbucket");
		writeLine("delete <id>");
		writeLine("deleteall [prefix]");
		writeLine("deletebucket");
		writeLine("exit");
		writeLine("get <id>");
		writeLine("getacl ['bucket'|'item'] <id>");
		writeLine("getfile <id> <file>");
		writeLine("getfilez <id> <file>");
		writeLine("gettorrent <id>");
		writeLine("head ['bucket'|'item'] <id>");
		writeLine("host [hostname]");
		writeLine("list [prefix] [max]");
		writeLine("listatom [prefix] [max]");
		writeLine("listrss [prefix] [max]");
		writeLine("listbuckets");
		writeLine("pass [password]");
		writeLine("put <id> <data>");
		writeLine("putfile <id> <file>");
		writeLine("putfilecontenttype <id> <file> <content-type>");
		writeLine("putfilez <id> <file>");
		writeLine("putfilewacl <id> <file> ['private'|'public-read'|'public-read-write'|'authenticated-read']");
		writeLine("putfilezwacl <id> <file> ['private'|'public-read'|'public-read-write'|'authenticated-read']");
		writeLine("quit");
		writeLine("setacl ['bucket'|'item'] <id> ['private'|'public-read'|'public-read-write'|'authenticated-read']");
		writeLine("time ['none'|'long'|'all']");
		writeLine("threads [num]");
		writeLine("user [username]");
	}

	/**
	 * Verifies that we are connected to the S3 host
	 * 
	 * @return true if we are connected to S3 host
	 */
	private boolean connectedToS3Host() {
		return m_authConn != null;
	}

	private String getACLTemplatePublicRead(String selfId) {
		StringBuffer acl = new StringBuffer();
		acl.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		acl
				.append("<AccessControlPolicy xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
		acl.append("<Owner>");
		acl.append("<ID>" + selfId + "</ID>");
		acl.append("</Owner>");
		acl.append("<AccessControlList>");
		acl.append("<Grant>");
		acl
				.append("<Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"CanonicalUser\">");
		acl.append("<ID>" + selfId + "</ID>");
		acl.append("</Grantee>");
		acl.append("<Permission>FULL_CONTROL</Permission>");
		acl.append("</Grant>");
		acl.append("<Grant>");
		acl
				.append("<Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"Group\">");
		acl
				.append("<URI>http://acs.amazonaws.com/groups/global/AllUsers</URI>");
		acl.append("</Grantee>");
		acl.append("<Permission>READ</Permission>");
		acl.append("</Grant>");
		acl.append("</AccessControlList>");
		acl.append("</AccessControlPolicy>");

		return acl.toString();
	}

	private String getACLTemplatePublicReadWrite(String selfId) {
		StringBuffer acl = new StringBuffer();
		acl.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		acl
				.append("<AccessControlPolicy xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
		acl.append("<Owner>");
		acl.append("<ID>" + selfId + "</ID>");
		acl.append("</Owner>");
		acl.append("<AccessControlList>");
		acl.append("<Grant>");
		acl
				.append("<Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"CanonicalUser\">");
		acl.append("<ID>" + selfId + "</ID>");
		acl.append("<DisplayName>duspense</DisplayName>");
		acl.append("</Grantee>");
		acl.append("<Permission>FULL_CONTROL</Permission>");
		acl.append("</Grant>");
		acl.append("<Grant>");
		acl
				.append("<Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"Group\">");
		acl
				.append("<URI>http://acs.amazonaws.com/groups/global/AllUsers</URI>");
		acl.append("</Grantee>");
		acl.append("<Permission>READ</Permission>");
		acl.append("</Grant>");
		acl.append("<Grant>");
		acl
				.append("<Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"Group\">");
		acl
				.append("<URI>http://acs.amazonaws.com/groups/global/AllUsers</URI>");
		acl.append("</Grantee>");
		acl.append("<Permission>WRITE</Permission>");
		acl.append("</Grant>");
		acl.append("</AccessControlList>");
		acl.append("</AccessControlPolicy>");

		return acl.toString();
	}

	private String getACLTemplatePrivate(String selfId) {
		StringBuffer acl = new StringBuffer();
		acl.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		acl
				.append("<AccessControlPolicy xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
		acl.append("<Owner>");
		acl.append("<ID>" + selfId + "</ID>");
		acl.append("</Owner>");
		acl.append("<AccessControlList>");
		acl.append("<Grant>");
		acl
				.append("<Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"CanonicalUser\">");
		acl.append("<ID>" + selfId + "</ID>");
		acl.append("</Grantee>");
		acl.append("<Permission>FULL_CONTROL</Permission>");
		acl.append("</Grant>");
		acl.append("</AccessControlList>");
		acl.append("</AccessControlPolicy>");

		return acl.toString();
	}

	private String createRSSDocument(List<ListEntry> ids) {
		S3RSSHelper rssHelper = new S3RSSHelper();
		rssHelper.createRSSDocument();
		rssHelper.openRSSDocument();
		rssHelper.openRSSChannel();
		String bucketurl = "http://" + m_host + "/" + m_bucket;
		String channeldesc = "Amazon S3 contents for bucket " + bucketurl;
		rssHelper.addRSSChannelInfo(channeldesc, bucketurl, channeldesc,
				new Date(), "en");
		for (ListEntry id : ids) {
			rssHelper.openRSSItem();
			String displayName = "yourname@yourhost.com";
			String itemurl = "http://" + m_host + "/" + m_bucket + "/" + id.key;
			String itemtitle = "Amazon S3 item " + itemurl;

			rssHelper.addRSSItemInfo(itemtitle, displayName, itemurl,
					itemtitle, new Date(), itemurl + "?torrent");
			rssHelper.addRSSItemEnclosure(itemurl, "" + id.size, "audio/mpeg");
			rssHelper.closeRSSItem();
		}
		rssHelper.closeRSSChannel();
		rssHelper.closeRSSDocument();
		return rssHelper.getRSSDocument();
	}

	private String createAtomDocument(List<ListEntry> ids) {
		S3AtomHelper atomHelper = new S3AtomHelper();
		atomHelper.createAtomDocument();
		atomHelper.openAtomDocument();
		String name = "yourname";
		String email = "yourname@yourhost.com";
		String bucketurl = "http://" + m_host + "/" + m_bucket;
		String feeddesc = "Amazon S3 contents for bucket " + bucketurl;
		atomHelper.addAtomFeedInfo(feeddesc, feeddesc, bucketurl, new Date(),
				name, email, bucketurl);
		for (ListEntry id : ids) {
			atomHelper.openAtomEntry();
			String itemurl = "http://" + m_host + "/" + m_bucket + "/" + id.key;
			String itemtitle = "Amazon S3 item " + itemurl;

			atomHelper.addAtomEntryInfo(itemtitle, itemurl, itemurl
					+ "?torrent", new Date(), itemtitle);
			atomHelper.closeAtomEntry();
		}
		atomHelper.closeAtomDocument();
		return atomHelper.getAtomDocument();
	}

}
