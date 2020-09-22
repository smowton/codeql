
#include "stl.h"

using namespace std;

char *source();

void sink(char *);
void sink(const char *);
void sink(std::pair<char *, char *>);
void sink(std::map<char *, char *>);
void sink(std::map<char *, char *>::iterator);
void sink(std::unordered_map<char *, char *>);
void sink(std::unordered_map<char *, char *>::iterator);

void test_pair()
{
	std::pair<char *, char *> a, b, c;

	a.first = "123";
	sink(a.first);
	sink(a.second);
	sink(a);

	b.first = source();
	sink(b.first); // tainted
	sink(b.second);
	sink(b); // tainted [NOT DETECTED]

	c.second = source();
	sink(c.first);
	sink(c.second); // tainted
	sink(c); // tainted [NOT DETECTED]

	std::pair<char *, char *> d("123", "456");
	sink(d.first);
	sink(d.second);
	sink(d);

	std::pair<char *, char *> e(source(), "456");
	sink(e.first); // tainted [NOT DETECTED]
	sink(e.second);
	sink(e); // tainted [NOT DETECTED]

	std::pair<char *, char *> f("123", source());
	sink(f.first);
	sink(f.second); // tainted [NOT DETECTED]
	sink(f); // tainted [NOT DETECTED]

	std::pair<char *, char *> g(f);
	sink(g.first);
	sink(g.second); // tainted [NOT DETECTED]
	sink(g); // tainted [NOT DETECTED]
	
	std::pair<char *, char *> h;
	h = f;
	sink(h.first);
	sink(h.second); // tainted [NOT DETECTED]
	sink(h); // tainted [NOT DETECTED]

	std::pair<char *, char *> i("123", "456");
	std::pair<char *, char *> j("123", source());
	std::pair<char *, char *> k("123", source());
	std::pair<char *, char *> l("123", "456");
 	i.swap(j);
	k.swap(l);
	sink(i.first);
	sink(i.second); // tainted [NOT DETECTED]
	sink(i); // tainted [NOT DETECTED]
	sink(j.first);
	sink(j.second);
	sink(j);
	sink(k.first);
	sink(k.second);
	sink(k);
	sink(l.first);
	sink(l.second); // tainted [NOT DETECTED]
	sink(l); // tainted [NOT DETECTED]

	sink(make_pair("123", "456"));
	sink(make_pair("123", "456").first);
	sink(make_pair("123", "456").second);
	sink(make_pair(source(), "456")); // tainted [NOT DETECTED]
	sink(make_pair(source(), "456").first); // tainted [NOT DETECTED]
	sink(make_pair(source(), "456").second);
	sink(make_pair("123", source())); // tainted [NOT DETECTED]
	sink(make_pair("123", source()).first);
	sink(make_pair("123", source()).second); // tainted [NOT DETECTED]

	std::pair<std::pair<char *, char *>, char *> m;
	m = make_pair(make_pair("123", source()), "789");
	sink(m); // tainted [NOT DETECTED]
	sink(m.first); // tainted [NOT DETECTED]
	sink(m.first.first);
	sink(m.first.second); // tainted [NOT DETECTED]
	sink(m.second);
}

void test_map()
{
	// insert
	std::map<char *, char *> m1, m2, m3, m4, m5, m6;

	sink(m1.insert(std::make_pair("abc", "def")).first);
	sink(m2.insert(std::make_pair("abc", source())).first); // tainted [NOT DETECTED]
	sink(m3.insert(std::make_pair(source(), "def")).first); // tainted [NOT DETECTED]
	sink(m4.insert(m4.begin(), std::pair<char *, char *>("abc", source()))); // tainted [NOT DETECTED]
	sink(m5.insert_or_assign("abc", source()).first); // tainted [NOT DETECTED]
	sink(m6.insert_or_assign(m6.begin(), "abc", source())); // tainted [NOT DETECTED]
	sink(m1);
	sink(m2); // tainted [NOT DETECTED]
	sink(m3); // tainted [NOT DETECTED]
	sink(m4); // tainted [NOT DETECTED]
	sink(m5); // tainted [NOT DETECTED]
	sink(m6); // tainted [NOT DETECTED]
	sink(m1.find("abc"));
	sink(m2.find("abc")); // tainted [NOT DETECTED]
	sink(m3.find("abc"));
	sink(m4.find("abc")); // tainted [NOT DETECTED]
	sink(m5.find("abc")); // tainted [NOT DETECTED]
	sink(m6.find("abc")); // tainted [NOT DETECTED]
	sink(m1.find("def"));
	sink(m2.find("def"));
	sink(m3.find("def"));
	sink(m4.find("def"));
	sink(m5.find("def"));
	sink(m6.find("def"));

	// copy constructors and assignment
	std::map<char *, char *> m7(m2);
	std::map<char *, char *> m8 = m2;
	std::map<char *, char *> m9;
	m9 = m2;
	sink(m7); // tainted [NOT DETECTED]
	sink(m8); // tainted [NOT DETECTED]
	sink(m9); // tainted [NOT DETECTED]
	sink(m7.find("abc")); // tainted [NOT DETECTED]
	sink(m8.find("abc")); // tainted [NOT DETECTED]
	sink(m9.find("abc")); // tainted [NOT DETECTED]

	// iterators
	std::map<char *, char *>::iterator i1, i2, i3;
	for (i1 = m1.begin(); i1 != m1.end(); i1++)
	{
		sink(*i1);
		sink(i1->first);
		sink(i1->second);
	}
	for (i2 = m2.begin(); i2 != m2.end(); i2++)
	{
		sink(*i2); // tainted [NOT DETECTED]
		sink(i2->first);
		sink(i2->second); // tainted [NOT DETECTED]
	}
	for (i3 = m3.begin(); i3 != m3.end(); i3++)
	{
		sink(*i3); // tainted [NOT DETECTED]
		sink(i2->first); // tainted [NOT DETECTED]
		sink(i2->second);
	}

	// array-like access
	std::map<char *, char *> m10, m11, m12, m13;
	sink(m10["abc"] = "def");
	sink(m11["abc"] = source()); // tainted
	sink(m12.at("abc") = "def");
	sink(m13.at("abc") = source()); // tainted
	sink(m10["abc"]);
	sink(m11["abc"]); // tainted [NOT DETECTED]
	sink(m12["abc"]);
	sink(m13["abc"]); // tainted [NOT DETECTED]

	// ranges
	std::map<char *, char *> m14;
	m14.insert(std::make_pair("a", "a"));
	m14.insert(std::make_pair("b", source()));
	m14.insert(std::make_pair("c", source()));
	m14.insert(std::make_pair("d", "d"));
	sink(m2.lower_bound("b")); // tainted [NOT DETECTED]
	sink(m2.upper_bound("b")); // tainted [NOT DETECTED]
	sink(m2.equal_range("b").first); // tainted [NOT DETECTED]
	sink(m2.equal_range("b").second); // tainted [NOT DETECTED]
	sink(m2.upper_bound("c"));
	sink(m2.equal_range("c").second);

	// swap
	std::map<char *, char *> m15, m16, m17, m18;
	m15.insert(std::pair<char *, char *>(source(), source()));
	m18.insert(std::pair<char *, char *>(source(), source()));
	sink(m15); // tainted [NOT DETECTED]
	sink(m16);
	sink(m17);
	sink(m18); // tainted [NOT DETECTED]
	m15.swap(m16);
	m17.swap(m18);
	sink(m15);
	sink(m16); // tainted [NOT DETECTED]
	sink(m17); // tainted [NOT DETECTED]
	sink(m18);

	// merge
	std::map<char *, char *> m19, m20, m21, m22;
	m19.insert(std::pair<char *, char *>(source(), source()));
	m20.insert(std::pair<char *, char *>("abc", "def"));
	m21.insert(std::pair<char *, char *>("abc", "def"));
	m22.insert(std::pair<char *, char *>(source(), source()));
	sink(m19); // tainted [NOT DETECTED]
	sink(m20);
	sink(m21);
	sink(m22); // tainted [NOT DETECTED]
	m15.merge(m16);
	m17.merge(m18);
	sink(m19); // tainted [NOT DETECTED]
	sink(m20); // tainted [NOT DETECTED]
	sink(m21); // tainted [NOT DETECTED]
	sink(m22); // tainted [NOT DETECTED]

	// erase, clear
	std::map<char *, char *> m23;
	m23.insert(std::pair<char *, char *>(source(), source()));
	m23.insert(std::pair<char *, char *>(source(), source()));
	sink(m23); // tainted [NOT DETECTED]
	sink(m23.erase(m23.begin())); // tainted [NOT DETECTED]
	sink(m23); // tainted [NOT DETECTED]
	m23.clear();
	sink(m23);

	// emplace, emplace_hint
	std::map<char *, char *> m24, m25;
	sink(m24.emplace("abc", "def").first);
	sink(m24);
	sink(m24.emplace("abc", source()).first); // tainted [NOT DETECTED]
	sink(m24); // tainted [NOT DETECTED]
	sink(m25.emplace_hint(m25.begin(), "abc", "def"));
	sink(m25);
	sink(m25.emplace_hint(m25.begin(), "abc", source())); // tainted [NOT DETECTED]
	sink(m25); // tainted [NOT DETECTED]
	
	// try_emplace
	std::map<char *, char *> m26, m27;
	sink(m26.try_emplace("abc", "def").first);
	sink(m26);
	sink(m26.try_emplace("abc", source()).first); // tainted [NOT DETECTED]
	sink(m26); // tainted [NOT DETECTED]
	sink(m27.try_emplace(m27.begin(), "abc", "def"));
	sink(m27);
	sink(m27.try_emplace(m27.begin(), "abc", source())); // tainted [NOT DETECTED]
	sink(m27); // tainted [NOT DETECTED]
}

void test_unordered_map()
{
	// insert
	std::unordered_map<char *, char *> m1, m2, m3, m4, m5, m6;

	sink(m1.insert(std::make_pair("abc", "def")).first);
	sink(m2.insert(std::make_pair("abc", source())).first); // tainted [NOT DETECTED]
	sink(m3.insert(std::make_pair(source(), "def")).first); // tainted [NOT DETECTED]
	sink(m4.insert(m4.begin(), std::pair<char *, char *>("abc", source()))); // tainted [NOT DETECTED]
	sink(m5.insert_or_assign("abc", source()).first); // tainted [NOT DETECTED]
	sink(m6.insert_or_assign(m6.begin(), "abc", source())); // tainted [NOT DETECTED]
	sink(m1);
	sink(m2); // tainted [NOT DETECTED]
	sink(m3); // tainted [NOT DETECTED]
	sink(m4); // tainted [NOT DETECTED]
	sink(m5); // tainted [NOT DETECTED]
	sink(m6); // tainted [NOT DETECTED]
	sink(m1.find("abc"));
	sink(m2.find("abc")); // tainted [NOT DETECTED]
	sink(m3.find("abc"));
	sink(m4.find("abc")); // tainted [NOT DETECTED]
	sink(m5.find("abc")); // tainted [NOT DETECTED]
	sink(m6.find("abc")); // tainted [NOT DETECTED]
	sink(m1.find("def"));
	sink(m2.find("def"));
	sink(m3.find("def"));
	sink(m4.find("def"));
	sink(m5.find("def"));
	sink(m6.find("def"));

	// copy constructors and assignment
	std::unordered_map<char *, char *> m7(m2);
	std::unordered_map<char *, char *> m8 = m2;
	std::unordered_map<char *, char *> m9;
	m9 = m2;
	sink(m7); // tainted [NOT DETECTED]
	sink(m8); // tainted [NOT DETECTED]
	sink(m9); // tainted [NOT DETECTED]
	sink(m7.find("abc")); // tainted [NOT DETECTED]
	sink(m8.find("abc")); // tainted [NOT DETECTED]
	sink(m9.find("abc")); // tainted [NOT DETECTED]

	// iterators
	std::unordered_map<char *, char *>::iterator i1, i2, i3;
	for (i1 = m1.begin(); i1 != m1.end(); i1++)
	{
		sink(*i1);
		sink(i1->first);
		sink(i1->second);
	}
	for (i2 = m2.begin(); i2 != m2.end(); i2++)
	{
		sink(*i2); // tainted [NOT DETECTED]
		sink(i2->first);
		sink(i2->second); // tainted [NOT DETECTED]
	}
	for (i3 = m3.begin(); i3 != m3.end(); i3++)
	{
		sink(*i3); // tainted [NOT DETECTED]
		sink(i2->first); // tainted [NOT DETECTED]
		sink(i2->second);
	}

	// array-like access
	std::unordered_map<char *, char *> m10, m11, m12, m13;
	sink(m10["abc"] = "def");
	sink(m11["abc"] = source()); // tainted
	sink(m12.at("abc") = "def");
	sink(m13.at("abc") = source()); // tainted
	sink(m10["abc"]);
	sink(m11["abc"]); // tainted [NOT DETECTED]
	sink(m12["abc"]);
	sink(m13["abc"]); // tainted [NOT DETECTED]

	// ranges
	std::unordered_map<char *, char *> m14;
	m14.insert(std::make_pair("a", "a"));
	m14.insert(std::make_pair("b", source()));
	m14.insert(std::make_pair("c", source()));
	m14.insert(std::make_pair("d", "d"));
	sink(m2.equal_range("b").first); // tainted [NOT DETECTED]
	sink(m2.equal_range("b").second); // tainted [NOT DETECTED]
	sink(m2.equal_range("c").second);

	// swap
	std::unordered_map<char *, char *> m15, m16, m17, m18;
	m15.insert(std::pair<char *, char *>(source(), source()));
	m18.insert(std::pair<char *, char *>(source(), source()));
	sink(m15); // tainted [NOT DETECTED]
	sink(m16);
	sink(m17);
	sink(m18); // tainted [NOT DETECTED]
	m15.swap(m16);
	m17.swap(m18);
	sink(m15);
	sink(m16); // tainted [NOT DETECTED]
	sink(m17); // tainted [NOT DETECTED]
	sink(m18);

	// merge
	std::unordered_map<char *, char *> m19, m20, m21, m22;
	m19.insert(std::pair<char *, char *>(source(), source()));
	m20.insert(std::pair<char *, char *>("abc", "def"));
	m21.insert(std::pair<char *, char *>("abc", "def"));
	m22.insert(std::pair<char *, char *>(source(), source()));
	sink(m19); // tainted [NOT DETECTED]
	sink(m20);
	sink(m21);
	sink(m22); // tainted [NOT DETECTED]
	m15.merge(m16);
	m17.merge(m18);
	sink(m19); // tainted [NOT DETECTED]
	sink(m20); // tainted [NOT DETECTED]
	sink(m21); // tainted [NOT DETECTED]
	sink(m22); // tainted [NOT DETECTED]

	// erase, clear
	std::unordered_map<char *, char *> m23;
	m23.insert(std::pair<char *, char *>(source(), source()));
	m23.insert(std::pair<char *, char *>(source(), source()));
	sink(m23); // tainted [NOT DETECTED]
	sink(m23.erase(m23.begin())); // tainted [NOT DETECTED]
	sink(m23); // tainted [NOT DETECTED]
	m23.clear();
	sink(m23);

	// emplace, emplace_hint
	std::unordered_map<char *, char *> m24, m25;
	sink(m24.emplace("abc", "def").first);
	sink(m24);
	sink(m24.emplace("abc", source()).first); // tainted [NOT DETECTED]
	sink(m24); // tainted [NOT DETECTED]
	sink(m25.emplace_hint(m25.begin(), "abc", "def"));
	sink(m25);
	sink(m25.emplace_hint(m25.begin(), "abc", source())); // tainted [NOT DETECTED]
	sink(m25); // tainted [NOT DETECTED]
	
	// try_emplace
	std::unordered_map<char *, char *> m26, m27;
	sink(m26.try_emplace("abc", "def").first);
	sink(m26);
	sink(m26.try_emplace("abc", source()).first); // tainted [NOT DETECTED]
	sink(m26); // tainted [NOT DETECTED]
	sink(m27.try_emplace(m27.begin(), "abc", "def"));
	sink(m27);
	sink(m27.try_emplace(m27.begin(), "abc", source())); // tainted [NOT DETECTED]
	sink(m27); // tainted [NOT DETECTED]
}
