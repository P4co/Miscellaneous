# -*- coding: utf-8 -*-
import pandas as pd

from sklearn.externals import joblib
from nltk.stem import SnowballStemmer
from nltk.corpus import stopwords
from string import punctuation
from nltk.tokenize import word_tokenize

spanish_stopwords = stopwords.words('spanish')
non_words = list(punctuation)

non_words.extend([u'¿', u'¡'])
non_words.extend(map(str, range(10)))

# based on http://www.cs.duke.edu/courses/spring14/compsci290/assignments/lab02.html
stemmer = SnowballStemmer('spanish')
def stem_tokens(tokens, stemmer):
    stemmed = []
    for item in tokens:
        stemmed.append(stemmer.stem(item))
    return stemmed

def tokenize(text):
    # remove non letters
    text = ''.join([c for c in text if c not in non_words])
    # tokenize
    tokens = word_tokenize(text)

    # stem
    try:
        stems = stem_tokens(tokens, stemmer)
    except Exception as e:
        print(e)
        print(text)
        stems = ['']
    return stems


if __name__ == '__main__':

    pd.set_option('max_colwidth', 1000)

    path_xml = './xml/'
    path_csv = './csv/'

    #grid_search = joblib.load('grid_search.pkl')
    #print grid_search
    #print grid_search.best_params_
    '''
    {
        'vect__ngram_range': (1, 1), 
        'cls__loss': 'hinge', 
        'vect__max_df': 0.5, 
        'cls__max_iter': 500, 
        'vect__min_df': 10, 
        'vect__max_features': 1000, 
        'cls__C': 0.2
    }
    '''

    pipeline = joblib.load('pipeline.pkl')

    print pipeline

    #tweets = pd.read_csv(path_csv + 'tweets.csv', encoding='utf-8')
    tweets = pd.read_csv(path_csv + 'tweets_Deber2.csv', encoding='utf-8')
    tweets = tweets[tweets.text.str.len() < 150]

    tweets['polarity'] = pipeline.predict(tweets.text)

    #tweets.to_csv(path_csv + 'tweets_2.csv', index=False, encoding='utf-8')
    #tweets.to_csv(path_csv + 'tweets_satya_3.csv', index=False, encoding='utf-8')
    tweets.to_csv(path_csv + 'tweets_Deber24.csv', index=False, encoding='utf-8')