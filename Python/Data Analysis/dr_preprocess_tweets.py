# -*- coding: utf-8 -*-
import pandas as pd
import json
from pandas import DataFrame
import numpy as np

if __name__ == '__main__':
    pd.set_option('max_colwidth',1000)

    path_csv = './csv/'
    file_path = './collected.json'
    #file_path = './collected_satya.json'

    data = []

    with open(file_path) as json_file:
        for line in json_file:
            if line.rstrip(' \n') != '':
               obj = json.loads(line.rstrip('\r\n'))
               if obj['text'] is not np.nan:
                   if obj['text'] != '':
                       data.append( (obj['id'], '@' + obj['user']['screen_name'], obj['text']) )


    df = DataFrame(data,columns=['id', 'screen_name', 'text'])
    #df.to_csv(path_csv+'tweets.csv',index=False, encoding='utf-8')
    df.to_csv(path_csv + 'tweets_Deber2.csv', index=False, encoding='utf-8')