import pandas as pd
import sys

fileNames= sys.argv
input_fileName=fileNames[1]
output_fileName1=fileNames[2]
output_fileName2=fileNames[3]

df=pd.read_excel(input_fileName)
df=df.fillna('')

env=str(df.iloc[0,0])
if len(env) <= 8:
  en = 8-len(env)
  env+= ' '*en

stage=str(df.iloc[0,1])

userid=str(df.iloc[0,2])
if len(userid) <= 6:
  en = 6-len(userid)
  userid+= ' '*en

ccid=str(df.iloc[0,3])
if len(ccid) <= 12:
  en = 12-len(ccid)
  ccid+= ' '*en

comm=str(df.iloc[0,4])
if len(comm) <= 39:
  en = 39-len(comm)
  comm+= ' '*en

common_stuff=env+stage+' '+userid+' '+ccid+' '+comm

f2 = open(output_fileName2, 'w')
f1 = open(output_fileName1, 'w')
f1.write(common_stuff)

for i in range(5,df.shape[1]):
    for j in range(df.shape[0]):
        if str(df.iloc[j,i]) != '':
            if len(str(df.iloc[j,i])) <= 8:
                offset = 8-len(str(df.iloc[j,i]))
                a = str(df.iloc[j,i])
                a+= ' '*offset
                b = str(df.columns[i])
                offset = 8-len(b)
                b+= ' '* offset
            f2.write((a)+' '+b+'''\n''')        
f2.close()
f1.close()
