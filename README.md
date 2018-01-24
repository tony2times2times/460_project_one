***Project 1***
**20 points**

Write a sender program and a receiver program, where the sender accepts a file as a command line parameter (any file on your hard disk), breaks it into smaller chunks (assume at least 12 chunks for your file), and sends it to the receiver. The receiver will concatenate the pieces it receives and will store it to a file on its end. Note that you would have at least 12 datagrams sent by the sender to the receiver. The sender and receiver will write out a detailed log of what they are doing to the console.
For instance, for each datagram sent the sender will write the [packet#]-[start byte offset]-[end byte offset]-[text sent].
Likewise, for each datagram received, the receiver will write the [packet#]-[start byte offset]-[end byte offset]-[text received].
Upload the completed Java code to your dropbox.

Sender.java: Seong-Chan Kang

Reciever.java: Tony Peraza
