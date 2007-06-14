jSh3ll (Amazon S3 command shell for Java)
------------------------------------------

The jSh3ll is a Java based command shell for managing your Amazon S3 objects.
It is built upon the Amazon S3 REST Java library and uses modified source code from the Amazon S3Shell. 
The following enhancements have been made: 
1. Leverages the Amazon REST library for Java. 
2. Support for ACL retrieval for buckets and items. 
3. Support for HEAD retrieval for buckets and items.
4. Support for getting a BitTorrent file (.torrent) for an item.
5. Support for ACL updating of buckets and items.
6. Support for creating a bucket with a specified ACL.
7. Support for putting an item with a specified ACL into a bucket.
8. Error handling.
9. File compression support (using ZLIB).
10. Script mode support.
11. Streaming file upload support.
12. Streaming file download support.
13. Bucket list as an Atom feed support.
14. Bucket list as an RSS feed support.
15. Copy an item from one bucket to another.
16. Copy all items from one bucket to another.
17. Putting a file with a specified content type.
 
Building jSh3ll
---------------

jSh3ll consists of one main file (jSh3ll.java - a modified Amazon S3Shell main class), the Amazon S3 REST Java library, and has no external dependencies
beyond the standard Java libraries.

Install the Java 5.0 VM.
jSh3ll uses generic collections and therefor requires a 5.0 Java VM.
Set your JAVA_HOME environment variable to refer to the 5.0 Java VM.

Build the code with ant. 
apache-ant-1.6.5 was used to build jSh3ll.

Running jSh3ll
--------------

A successfull build for jSh3ll will place the jSh3ll.jar in the dist directory.

Invoke the jSh3ll.jar and specify any of the following command line arguments:

-h [hostname] - The Amazon S3 hostname
-u [username] - The Access Key ID
-p [password] - The Secret Access Key
-b [bucket] - The default S3 bucket to use
-i [inputfile] - The input file to read jSh3ll commands from
-o [outputfile] - The output file to write all jSh3ll output to

Some example jSh3ll command line invocations are as follows:

1. Tell jSh3ll to connect to s3.amazonaws.com using the myaccesskeyid and mysecretaccessid and use the mybucket as the initial bucket.

java -jar dist/jSh3ll.jar -h s3.amazonaws.com -u myaccesskeyid -p mysecretaccessid -b mybucket

2. Start jSh3ll with no arguments and then use the "host", "user" and "pass" commands to set up all this information.

java -jar dist/jSh3ll.jar

3. Run jSh3ll in script mode reading all jSh3ll input commands from s3commands.txt and print all output to s3output.txt

java -jar dist/jSh3ll.jar -h s3.amazonaws.com -u myaccesskeyid -p mysecretaccessid -i s3commands.txt -o s3output.txt

Using jSh3ll
------------

Once you have jSh3ll started, you can type "help" at the prompt to get a quick
reminder of the commands. jSh3ll isn't very helpful when you misuse commands,
it will just tell you tersely what it expects. Most usage errors will be
signaled by a terse error message, but a few will result in
IllegalArgumentException stack traces being printed to the screen.

* bucket [bucketname]

Changes the current bucket, or displays the current bucket if one is set. The
current bucket need not exist, in fact, the only way to create one is to change
the current bucket and then use "createbucket".

* copy <id> <src_bucket> <dest_bucket> [user] [password]

Copies an item from a source bucket to a destination bucket.
If the destination bucket is owned by another S3 user, you can specify the access key id and secret access key of that S3 account.

* copyall [prefix] <src_bucket> <dest_bucket> [user] [password]

Copies all items, with an optional prefix, from a source bucket to a destination bucket.
If the destination bucket is owned by another S3 user, you can specify the access key id and secret access key of that S3 account.

* count [prefix]

Counts the number of items in the current bucket. If your bucket contains a lot
of items, this could take a long time to run since the only way of counting the
items is to list them all. If the prefix is specified, only count the items in
the bucket that have this prefix in the ID.

* createbucket

Creates the current bucket.

* createbucket ['private'|'public-read'|'public-read-write'|'authenticated-read']

Creates the current with the ACL specified.

* delete <id>

Deletes an item from the current bucket.

* deleteall [prefix]

Deletes all the items from the current bucket. Be careful with this one, it
won't ask you to make sure you're sure. By specifying a prefix, you can limit
the deletion to only items that have this prefix in their ID. This command
could take a while to run if your bucket has a lot of items. For one way to
speed it up, see the "threads" command.

* deletebucket

Deletes the current bucket.

* get <id>

Gets the item with the given ID and displays it to the terminal.

* getacl ['bucket'|'item'] <id>

Gets the ACL for a bucket or item with the given ID.

* getfile <id> <file>

Gets the item with the given ID and stores it in the specified file.

* getfilez <id> <file>

Gets the ZLIB compressed item with the given ID and stores it in the specified file.

* gettorrent <id> <file>

Gets the BitTorrent file (.torrent) of the given ID and stores it.

* head ['bucket'|'item'] <id>

Gets the head information for a bucket or item identified by <id>.
Use this to retrieve information about a specific object, without actually fetching the object itself. 
This is useful if you're only interested in the object metadata, and don't want to waste bandwidth on the object data.

* host [hostname]

Sets the S3 host to the given hostname, or displays the current host if no argument is given.

* list [prefix] [max]

List the items in the current bucket, subject to the given constraints. 
If prefix is specified, the listing is limited to the given prefix.
You may use the wildcard '*' or no prefix to list ALL ITEMS). 
If max is specified, the number of returned results will be limited
to max items. 
The S3 server may impose its own limits on the number of items returned.

* listatom [prefix] [max]

List the items in the current bucket as a valid Atom 1.0 feed, subject to the given constraints. 
If prefix is specified, the listing is limited to the given prefix.
You may use the wildcard '*' or no prefix to list ALL ITEMS). 
If max is specified, the number of returned results will be limited
to max items. 
The S3 server may impose its own limits on the number of items returned.

* listrss [prefix] [max]

List the items in the current bucket as a valid RSS 2.0 feed, subject to the given constraints. 
If prefix is specified, the listing is limited to the given prefix.
You may use the wildcard '*' or no prefix to list ALL ITEMS). 
If max is specified, the number of returned results will be limited
to max items. 
The S3 server may impose its own limits on the number of items returned.

* listbuckets

Lists all the buckets belonging to the Access Key ID currently in use.

* pass [password]

Sets the Secret Access Key used to authenticate with S3.

* put <id> <data>

Stores the given data into S3 with the given ID. The data is limited to a
single line. See putfile for a way to put more data.

* putfile <id> <file>

Stores the contents of the given file into S3 under the specified ID.

* putfile <id> <file> <content-type>

Stores the contents of the given file into S3 under the specified ID with a specified content type.

* putfilez <id> <file>

Stores the contents of the given file into S3 under the specified ID, using ZLIB compression.

* putfilewacl <id> <file> ['private'|'public-read'|'public-read-write'|'authenticated-read']

Stores the contents of the given file into S3 under the specified ID, with the specified ACL.

* putfilezwacl <id> <file> ['private'|'public-read'|'public-read-write'|'authenticated-read']

Stores the contents of the given file into S3 under the specified ID, with the specified ACL, using ZLIB compression..

* setacl ['bucket'|'item'] <id> ['private'|'public-read'|'public-read-write'|'authenticated-read']

Sets the ACL for a bucket or item of the specified ID.

* time ['none'|'long'|'all']

Controls the display of timing information for the commands executed by the
shell. The default is "long" which only displays execution times for commands
that run longer than five seconds. The only commands likely to have interesting
run times are the iterative ones like "list", "count", and "deleteall".

* threads [num]

This command enables multi-threaded execution of "deleteall" commands. The
default value is 1, which serially deletes items one at a time as they're
returned by S3. We find 10 to be a good number; this will parallelize deletions
with ten threads executing DELETEs against S3 concurrently. We've never noticed
any slowdown from using higher thread counts, but somewhere around 100 or so
the benefit seems to level off because IDs can't be retrieved from S3 faster than
they're being deleted.

* user [username]

Sets the Access Key ID used to authenticate with S3.


Amazon Digital Services disclosure
----------------------------------

This software code is made available "AS IS" without warranties of any
kind.  You may copy, display, modify and redistribute the software
code either by itself or as incorporated into your code; provided that
you do not remove any proprietary notices.  Your use of this software
code is at your own risk and you waive any claim against Amazon
Digital Services, Inc. or its affiliates with respect to your use of
this software code. (c) 2006 Amazon Digital Services, Inc. or its
affiliates.

SilvaSoft, Inc. disclosure
-------------------------
This software makes use source code provides by Amazon Digital Services, Inc.
Specifically, the Amazon S3 REST API for Java and the S3 Shell source code.
All modified source code (c) 2006 SilvaSoft, Inc. and licensed under the MIT License.
See LICENSE.txt for details.

For questions, bug reports or suggestions see contact info below.

Contact Info
------------
Email: dominic@silvasoftinc.com
Web: http://www.silvasoftinc.com
Blog: http://jroller.com/page/silvasoftinc
