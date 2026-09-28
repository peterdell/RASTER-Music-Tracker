#pragma once

#include <fstream>

extern CString GetFilePath(CString pathandfilename);

// std::istream& rather than std::ifstream& - every real call site passes a
// genuine file stream (which satisfies the wider base type), and the wider
// type lets tests use an in-memory stream.
extern BOOL NextSegment(std::istream& in);
extern char CharH4(unsigned char b);
extern char CharL4(unsigned char b);

extern void Trimstr(char* txt);
extern int Hexstr(char* txt, int len);