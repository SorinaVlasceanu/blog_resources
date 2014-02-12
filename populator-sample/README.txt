
How to build the JAR file.
=========================

**If you open this sample from your IDE, you need to point the wso2greg-4.6.0/repository/components/plugins to the classpath.


1. Open the build.xml file and change the value of "product.home" to your product home path.
   (You need to change this value : /home/ajith/wso2/product/wso2greg-4.6.0)

2. Run the "ant clean" inside the  populator-sample folder.

3. Run the "ant" inside populator-sample folder to build jar file.

4. Copy the jar file inside the target to the wso2greg-4.6.0/repository/components/lib and restart the server.


Note: When the server is restarting ,the copy of the jar file going to drop to the wso2greg-4.6.0/repository/components/dropings as well.
Therefore every time you modify the jar, you need to delete the previous jar file from following two locations to reflect the new changes.

i)  wso2greg-4.6.0/repository/components/dropings
ii) wso2greg-4.6.0/repository/components/lib
