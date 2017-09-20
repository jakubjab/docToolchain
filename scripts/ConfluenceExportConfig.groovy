// endpoint of the confluenceAPI (REST) to be used
confluenceAPI = ''

// the key of the confluence space to write to
confluenceSpaceKey = ''

input = ["title of the page"]

// username:password of an account which has the right permissions to create and edit
// confluence pages in the given space.
// if you want to store it securely, fetch it from some external storage.
// you might even want to prompt the user for the password like in this example
confluenceCredentials = "${System.getenv('USER')}:${System.console().readPassword(' confluence password: ')}".bytes.encodeBase64().toString()
