This is a version of apache chainsaw modified with a custom appender receiver that allows logs to come in from any device using the berserkr-logging slf4j mapper (https://github.com/motocoder/berserkr-logging). 

I will not discuss how to set up the client with berserkr-logging here because it is described on the github for that project.

To set up chainsaw to receive logs you simply pull down the source code from this project and launch the main() method inside the org.apache.log4j.chainsaw.ChainsawStarter class.

Once chainsaw is running simply add a receiver:

![receiver_click](https://github.com/user-attachments/assets/aa52eb0b-cef2-40d8-b024-1ecc6acf2fb5)

select PayloadProxyReceiver:

![payload_receiver_menu_item](https://github.com/user-attachments/assets/335a0ace-7f6f-4294-8dea-9674853db45a)

Enter in the GUID and password you want to use. These need to match what the client running berserkr-logging is using. The GUID needs to be unique and hard to come up with. The server will map the GUID to the 
password the first time you use it and then it becomes immutable. If it is already in use with a different password it will fail to connect:

![enter_guid_info](https://github.com/user-attachments/assets/77332b07-b124-46e6-9266-8477a0636060)

Then you will see a tab appear for the name of the receiver you just created, click it to see the logs coming to that receiver:

<img width="750" height="218" alt="receiver_tab" src="https://github.com/user-attachments/assets/f05a102d-0baf-4c88-b693-14bcc45d4386" />

Once the client connects logs will come into this screen.



