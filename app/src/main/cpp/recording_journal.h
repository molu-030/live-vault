#pragma once
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <fcntl.h>
#include <string>
#include <unistd.h>
#include <vector>
namespace recording {
struct JournalStream {const char* name;int rate,channels,bits,format;uint64_t frames;};
// Writer only; frames must already be flushed and synchronized independently.
struct RecordJournal {
 static bool write(const std::string& path,bool done,const std::vector<JournalStream>& streams){
  std::string json="{\"version\":1,\"done\":"+std::string(done?"true":"false");
  for(const auto& s:streams){std::string name=s.name?s.name:"";
   if(name!="mix"&&name!="raw"&&name!="fixed"&&name!="vocal")return false;
   if(s.rate<8000||s.rate>192000||s.channels<1||s.channels>2||!((s.format==1&&s.bits==16)||(s.format==3&&s.bits==32)))return false;
   json+=",\""+name+"\":{\"rate\":"+std::to_string(s.rate)+",\"channels\":"+std::to_string(s.channels)+",\"bits\":"+std::to_string(s.bits)+",\"format\":"+std::to_string(s.format)+",\"bytesPerFrame\":"+std::to_string(s.channels*(s.bits/8))+",\"frames\":"+std::to_string(s.frames)+"}";
  }
  json+="}\n";std::string temporary=path+".tmp";int fd=::open(temporary.c_str(),O_WRONLY|O_CREAT|O_TRUNC,0600);if(fd<0)return false;bool ok=true;size_t offset=0;
  while(offset<json.size()){ssize_t n=::write(fd,json.data()+offset,json.size()-offset);if(n<0&&errno==EINTR)continue;if(n<=0){ok=false;break;}offset+=size_t(n);}
  if(ok&&fsync(fd)!=0)ok=false;if(::close(fd)!=0)ok=false;
  if(!ok){unlink(temporary.c_str());return false;}if(rename(temporary.c_str(),path.c_str())!=0){unlink(temporary.c_str());return false;}
  auto slash=path.find_last_of('/');std::string parent=slash==std::string::npos?".":path.substr(0,slash);if(parent.empty())parent="/";
  int directory=::open(parent.c_str(),O_RDONLY|O_DIRECTORY);if(directory<0)return false;ok=fsync(directory)==0;::close(directory);return ok;
 }
};
}
