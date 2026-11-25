# 商业登录器网关。测试服 CentOS 7.6 + gcc 4.8.5 直接 make。
# 依赖：sqlite-devel（yum install -y sqlite-devel）、pthread。

CXX      ?= g++
CXXFLAGS ?= -std=c++11 -O2 -Wall -Wextra -Wno-unused-parameter
LDFLAGS  ?=
LDLIBS   ?= -lsqlite3 -lpthread

TARGET := a4gateway
SRC    := $(wildcard src/*.cpp)
OBJ    := $(patsubst src/%.cpp,build/%.o,$(SRC))

.PHONY: all clean test

all: $(TARGET)

$(TARGET): $(OBJ)
	$(CXX) $(CXXFLAGS) $(LDFLAGS) -o $@ $(OBJ) $(LDLIBS)

build/%.o: src/%.cpp
	@mkdir -p build
	$(CXX) $(CXXFLAGS) -c -o $@ $<

# 本机跑一遍协议探针（需要先在 conf/gateway.ini 指好库）
test: $(TARGET)
	./$(TARGET) conf/gateway.ini &
	sleep 1
	python3 tools/probe_gateway.py
	kill %1

clean:
	rm -rf build $(TARGET)
