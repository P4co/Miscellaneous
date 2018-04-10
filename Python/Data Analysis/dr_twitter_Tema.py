# -*- coding: utf-8 -*-
from tweepy.streaming import StreamListener
from tweepy import OAuthHandler
from tweepy import Stream
import json

# Twitter Credentials
# Access Token for Twitter App
access_token = "222335678-QJRLsu1if3HkwCBHwQ7feMG89dE4c0fh4axK8Pa5"
access_token_secret = "2E3SMSWlBdomFYDlROp6h64ak5VLveAKuRKWGYZnjTHNJ"
# Consumer Keys
consumer_key = "lA88E686KiXl6wE5h8CvpRb8h"
consumer_secret = "vTQqaxKAR5VGc3HQHTDSXCHnnS6NFErCYeVZ8eSvMBHG5HqIGq"

path = './collected_Deber2.json'

class StdOutListener(StreamListener):
    '''
    This function is called every time a new tweet is received
    on the stream
    '''
    def on_data(self, data):
        # Convert data to json object (note: this process may slow down performance)
        obj = json.loads(data)
        print obj['text'] + '\n'

        try:
            if obj['lang'] == 'es':
                #if 'lenin' in obj['text'].lower() or u'lenín' in obj['text'].upper() \
                #        or 'moreno' in obj['text'].lower() or 'jorge' in obj['text'].lower()\
                #        or 'glas' in obj['text'].lower():
                #if obj['place']['country'] == 'Ecuador':
                #print obj['user']['screen_name'] + ' -> ' + obj['text'] + ' <- '
                # Create a file to store output ('a' means to append)
                tweet = data.encode('utf8')
                f = open(path, 'a')
                f.write(tweet)
                f.close()
                #else:
                #    print obj['text']

        except Exception, e:
            print e
        # Just get the text of the tweet
        return True

    ''' This function is called whenever there is an error receiving a tweet. '''

    def on_error(self, status):
        print status
        return False

'''
Connects to the Twitter stream and listens for Twitter updates in
a specified location box around Ecuador.
'''
def start_twitter_stream():
    listener = StdOutListener()
    auth = OAuthHandler(consumer_key, consumer_secret)
    auth.set_access_token(access_token, access_token_secret)
    try:
        while True:
            try:
                # Connect to the Twitter stream
                stream = Stream(auth, listener)
                # Track geotagged tweets within a location box
                stream.filter(track=[u'Ecuador',u'Jorge',u'Espinel'],locations=[-95, -5, -76.345477, 0.691367])
            except Exception, e:
                print e
                continue
    except KeyboardInterrupt:
        # User pressed Ctrl+C. Get ready to exit the program
        print KeyboardInterrupt
        pass

if __name__ == '__main__':

    start_twitter_stream()